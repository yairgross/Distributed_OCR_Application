import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class Manager {

    /*
    waits for messages from applications.
     if the message is of a new task - downloads input file from S3

     manager creates all the threads when started.
     threads - wait for messages from apps queue and distribute between workers
             - wait for all urls to be processed by workers and create results file
             - wait for termination msg and terminate

     */
    Sqs sqs;
    SqsClient sqsClient;
    EC2 ec2;
    Ec2Client ec2Client;
    S3 s3;
    S3Client s3Client;
    int n; // Max number of task that each worker can execute
    int numOfMessages = 0;
    final Object numOfMessagesLock = new Object();
    int numOfWorkers = 0;
    final Object numOfWorkersLock = new Object();
    int INSTANCE_NUM_THRESHOLD = 18;
    String MANAGER_TO_WORKERS_QUEUE = "manager-to-workers-queue";
    String APPS_TO_MANAGER_QUEUE = "app-to-manager-queue";
    String WORKERS_TO_MANAGER_QUEUE = "workers-to-manager-queue";
    boolean terminate = false;
    Boolean completedAllTasks = false;

    ConcurrentLinkedQueue<String> workersIds = new ConcurrentLinkedQueue<>(); //for termination
    Map<String, Integer> taskNumPerApp = new ConcurrentHashMap<>(); //number of tasks(images) for each app
    Map<String, String> outputBucketsPerApp = new ConcurrentHashMap<>(); //results buckets for each app //todo only workerListener uses
    Map<String, String> inputBucketsPerApp = new ConcurrentHashMap<>();
    Map<String, String> managerToAppsQueues = new ConcurrentHashMap<>();//todo only workerListener uses

    Map<String, BlockingDeque<String[]>> resultMsgsPerApp = new ConcurrentHashMap<>();



    ExecutorService  executorService; //threads


    public Manager(int n){
//        this.sqsClient = SqsClient.builder().region(Region.US_EAST_1)
//                .credentialsProvider(ProfileCredentialsProvider.create())
//                .build();
//
//        this.ec2Client = Ec2Client.builder().region(Region.US_EAST_1)
//                .credentialsProvider(ProfileCredentialsProvider.create())
//                .build();
//
//        this.s3Client = S3Client.builder().region(Region.US_EAST_1)
//                .credentialsProvider(ProfileCredentialsProvider.create())
//                .build();

        this.sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
        this.ec2Client = Ec2Client.builder().region(Region.US_EAST_1).build();
        this.s3Client = S3Client.builder().region(Region.US_EAST_1).build();
        this.ec2 = new EC2(ec2Client);
        this.sqs = new Sqs(sqsClient);
        this.s3 = new S3(s3Client);


        this.n = n;
    }


    public void start(){
        System.out.print("manager started!");
        sqs.createQueue(MANAGER_TO_WORKERS_QUEUE);
        sqs.createQueue(APPS_TO_MANAGER_QUEUE);
        sqs.createQueue(WORKERS_TO_MANAGER_QUEUE);
        System.out.print("manager - created queues.");

        //starts the listener to the app queue
        executorService = Executors.newCachedThreadPool();
        executorService.execute(new AppsQueueListener(this, APPS_TO_MANAGER_QUEUE));
        executorService.execute(new WorkersQueueListener(this, WORKERS_TO_MANAGER_QUEUE));
        try {
            executorService.awaitTermination(15, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTerminate(){
        terminate = true;
    }
    public void terminate(){
        System.out.println("manager start termination process");
        executorService.shutdown();

        for(String workerId: workersIds){
            ec2.terminateEC2(workerId);
        }
        System.out.println("manager terminated workers");

        for(String bucket: outputBucketsPerApp.values()){
            s3.deleteObjectsInBucket(bucket);
            s3.deleteBucket(bucket);
        }
        for(String bucket: inputBucketsPerApp.values()){
            s3.deleteObjectsInBucket(bucket);
            s3.deleteBucket(bucket);
        }
        System.out.println("manager deleted buckets");

        sqs.deleteQueue(MANAGER_TO_WORKERS_QUEUE);
        sqs.deleteQueue(WORKERS_TO_MANAGER_QUEUE);
        sqs.deleteQueue(APPS_TO_MANAGER_QUEUE);
        for(String q: managerToAppsQueues.values()){
            sqs.deleteQueue(q);
        }
        System.out.println("manager deleted queues");


        try {
            Runtime.getRuntime().exec("sudo shutdown -h now"); //shutdown after 5 seconds
        } catch (IOException e) {
            System.out.println("error - manager could not terminate");
        }
    }

    public String[] getResultForApp(String appName) throws InterruptedException {
        return resultMsgsPerApp.get(appName).take();
    }

    public void putResultForApp(String appName, String[] result){
        resultMsgsPerApp.get(appName).add(result);

    }

    public void openThreadForApp(String appName, String bucket, String key){
        //opens an instance of TaskDistributer
        executorService.execute(new TasksDistributer(this, appName, bucket, key));
    }

    public void openResultUploaderForApp(String appName){
        executorService.execute(new ResultsUploaderPerApp(this, appName));
    }
    public void addResultsQueueForApp(String appName){
        resultMsgsPerApp.put(appName, new LinkedBlockingDeque<>());
    }

    public void decreaseTaskCountOfApp(String nameOfApp){
        Integer count = taskNumPerApp.get(nameOfApp);
        if(count != null){
            taskNumPerApp.put(nameOfApp, count - 1);
        }else{
            System.out.println("error - there is no task number for app"); //todo delete
        }
    }
    public int getNumOfTasksPerApp(String appName){
        Integer num = taskNumPerApp.get(appName);
        if(num != null){
            return num;
        }else{
            System.out.println("error - app does not have any tasks"); //todo delete
            return -1;
        }
    }


    public void uploadResultToS3(String appName, String imgURL, String text){
        // create output bucket for this app if it hasn't been created yet

        String bucket = outputBucketsPerApp.get(appName);
        if(bucket == null){
            bucket = "output-bucket-"+appName;
            s3.createBucket(bucket, Region.US_EAST_1);
            outputBucketsPerApp.put(appName, bucket);
        }

        // add the url and text to this bucket
//        List<ByteBuffer> lst = new ArrayList<>();
//        lst.add(ByteBuffer.wrap(text.getBytes()));

        String key = "" +new Date().getTime();
        String val = "url:" + imgURL+"\t" + text;
        s3.uploadStrToS3(bucket, key, ByteBuffer.wrap(val.getBytes()));
//        try {
//            s3.multipartUpload(bucket, imgURL, lst);
//        } catch (IOException e) {
//            System.out.println("error - couldn't upload results to app bucket");
//            throw new RuntimeException(e);
//        }

    }

    /*
    sends finish message to app indicating the output bucket.
     */
    public void sendFinishMessageToApp(String appName){
        String outputBucket = outputBucketsPerApp.get(appName);
        if (outputBucket == null)
            System.out.println("Error - output bucket does not exist for app");
        else{
            String message = "app:" + appName + "\n" + "bucket-name:" + outputBucket;
            sqs.sendMessageToQueue(managerToAppsQueues.get(appName), message);
        }
    }

    public void removeAppFromManager(String appName){
        taskNumPerApp.remove(appName);
//        managerToAppsQueues.remove(appName);
        if(taskNumPerApp.isEmpty()){
            completedAllTasks = true;
//            completedAllTasks.notifyAll();
        }

    }

    public boolean completedAllTasks(){
        return completedAllTasks;
    }


    public List<Message> receiveMessageFromQueue(String queueName){
        List<Message> messages = sqs.receiveMessageFromQueue(queueName);
        return messages;
    }

    public void createQueue(String appName ,String queueName){
        managerToAppsQueues.put(appName, queueName);
        sqs.createQueue(queueName);
    }

    /*
    downloads the file from s3.
     */
    public String[] downloadFileFromS3(String appName, String bucket, String key){
        String content = s3.getObject(bucket, key);
        String[] contentArr = content.split("\n");
        return contentArr;
    }

    public void insertNumOfTasksPerApp(String appName, int numOfTasks){
        taskNumPerApp.put(appName, numOfTasks);
        updateNumOfMessages(numOfTasks);

    }

    public void updateNumOfMessages(int toAdd){
        synchronized (numOfMessagesLock){
            numOfMessages += toAdd;
        }
    }

    public void addWorkersIfNeeded(){
        synchronized (numOfMessagesLock){
            synchronized (numOfWorkersLock){
                while(numOfWorkers < (int) Math.ceil((double) numOfMessages / n) && numOfWorkers < INSTANCE_NUM_THRESHOLD){
                    String workerId = ec2.createWorkerInstance("worker-" + numOfWorkers, "ami-0e82ae023db057ca1", n);
                    workersIds.add(workerId);
                    numOfWorkers++;
                    System.out.println("manager - added worker, num of workers" + numOfWorkers);
                }
            }
        }

    }

    public void sendTaskToWorkersQueue(String msg){
        sqs.sendMessageToQueue(MANAGER_TO_WORKERS_QUEUE, msg);
    }

    public void deleteMsgFromQueue(String queueName, Message msg){
        sqs.deleteMessageFromQueue(queueName, msg);
    }


    public boolean shouldTerminate(){
        return terminate;
    }
    public void addInputBucketForApp(String appName, String bucket){
        inputBucketsPerApp.put(appName, bucket);
    }
}
