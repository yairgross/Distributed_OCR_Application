import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.Thread.sleep;

public class Application {

    String APP_TO_MANAGER_QUEUE = "app-to-manager-queue";
    String MANAGER_TO_APP_QUEUE = "manager-to-app-queue-";
    File inputFile;
    File outputFile;
    int n; //maximum number of files for each worker
    boolean terminate; //should terminate or not
    S3 s3;
    EC2 ec2;
    Sqs sqs;
    S3Client s3Client;

    public Application(String inputFileName,String outputFileName, int n, boolean terminate) {
        this.inputFile = new File(inputFileName);
        this.outputFile = new File(outputFileName);
        try {
            this.outputFile.createNewFile();
        } catch (IOException e) {
            System.out.println("error - couldn't create the output file");
            throw new RuntimeException(e);
        }
        this.n = n;
        this.terminate = terminate;
    }

    /**
    starts the application.
     */
    public void start() throws InterruptedException {
        //initializes clients
        Ec2Client ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
        ec2 = new EC2(ec2Client);
        s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
        s3 = new S3(s3Client);
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
        sqs = new Sqs(sqsClient);

        //check if manager is up, if not creates a new instance
        System.out.println("app starting manager if not up");
        if(!ec2.checkIfManagerIsUp()){
            ec2.createManagerInstance("Manager", "ami-03c2d807fa9f967c6", n);
        }

        String name = "app-"+ new Date().getTime();
        MANAGER_TO_APP_QUEUE = MANAGER_TO_APP_QUEUE + name;
        String inputFileBucket = "input-file-bucket-" + name;
        String inputFileKey = "input-file-key-"+ name;
        System.out.println("app - creating input file bucket manager");
        s3.createBucket(inputFileBucket, Region.US_EAST_1);

        s3.uploadFileToS3(inputFileBucket, inputFileKey, inputFile);


        boolean sentMsg = false;
        while(!sentMsg){
            try{
                sqs.sendMessageToQueue(APP_TO_MANAGER_QUEUE, "name:"+ name+"\n"+
                        "queue-name:" + MANAGER_TO_APP_QUEUE+"\n"+
                        "input-file-bucket:" + inputFileBucket + "\n" +
                        "input-file-key:" + inputFileKey + "\n");
                sentMsg = true;
            }catch(QueueDoesNotExistException e){
                System.out.println("app to manager queue not created yet");
                sleep(5000);
            }
        }


        Message message = null;
        while(message == null) { //trys to get result message, waits for the queue to be created
            try {
                List<Message> messages = sqs.receiveOneMessageFromQueue(MANAGER_TO_APP_QUEUE);
                if(messages.isEmpty()){
                    continue;
                }
                message = messages.get(0); //only gets one message
                System.out.println("app received result from manager.");
            } catch (QueueDoesNotExistException e) {
                System.out.println("manager to app queue not created yet");
                sleep(3000);
            }
        }

        sqs.deleteMessageFromQueue(MANAGER_TO_APP_QUEUE, message);
        String[] details = message.body().split("\n");
        String bucketWithResults = details[1].substring(12);

        String results = s3.getAllObjectsInBucket(bucketWithResults);

        createHTML(results, outputFile);
        //sends a termination msg to the manager
        if(terminate){
            sqs.sendMessageToQueue(APP_TO_MANAGER_QUEUE, "terminate");
        }

        sqs.sendMessageToQueue(APP_TO_MANAGER_QUEUE, "app:" + name + "\n" + "finished");
    }

    /*
    divides the input file into 5mb byte buffers and uploads to S3
     */
//    public void uploadFileToS3(String bucketName, String key){
//        DataInputStream dataIn = null;
//        int byteCount = (int) inputFile.length();
//        try {
//            //creates a byte buffer with the file's bytes
//            FileInputStream in = new FileInputStream(inputFile);
//            dataIn = new DataInputStream(in);
//            final byte[] bytes = new byte[byteCount];
//            dataIn.readFully(bytes);
//
//            int indexInBuffer = 0;
//            List<ByteBuffer> lstOfBuffers = new ArrayList<>();
//            int mb5 = 5 * 1024 * 1024;
//
//            //divide file buffer to 5mb buffers
//            while(indexInBuffer < byteCount){
//                int toIndex = indexInBuffer + mb5;
//                if(toIndex > byteCount){
//                    toIndex = byteCount;
//                }
//                byte[] filePart = Arrays.copyOfRange(bytes, indexInBuffer, toIndex);
//                ByteBuffer filePartBuffer = ByteBuffer.wrap(filePart);
//                lstOfBuffers.add(filePartBuffer);
//                indexInBuffer += mb5;
//            }
//
//            //uploads the file to S3
//            s3.multipartUpload(bucketName, key, lstOfBuffers);
//
//        } catch (IOException e){
//            System.out.println("error - error in multipart upload.");
//            throw new RuntimeException(e);
//        }
//    }


    /*
     * create HTML file from results.
     */
    public void createHTML(String results, File file){

        String html = "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>OCR</title></head>";
        String[] imagesAndText = results.split("\n");
        for(String img: imagesAndText){
            String[] URLAndText = img.split("\t");
            String imgURL = URLAndText[0].substring(4);

            if(URLAndText.length > 1 && URLAndText[1].startsWith("text")){
                String text = URLAndText[1].substring(5);
                html += "<p><img src=\""+ imgURL+"\"> \n<br>\n "+ text +"</p>";
            }else{
                String err = URLAndText[1].substring(6);
                html += "<p>\""+ imgURL+"\"\n<br>\n"+ err +"</p>";
            }


        }

        try{
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(html);
            bw.close();
        }catch(IOException e){
            System.out.println("error - while creating html file");
            e.printStackTrace();
        }
    }
}
