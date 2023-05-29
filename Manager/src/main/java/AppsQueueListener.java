import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

public class AppsQueueListener implements Runnable{
    /*  Waits for messages from Apps_To_Manager_Queue
        Opens specific Manager_to_App_Queue for each Application
        Calls a function that updated the s3 file locations in the manager
        Updated the main manager thread if recieved termination message
    */
    Manager manager;
    String APPS_TO_MANAGER_QUEUE;
    public AppsQueueListener(Manager manager, String queueName){
        this.manager = manager;
        this.APPS_TO_MANAGER_QUEUE = queueName;
    }

    @Override
    public void run() {
        while (!manager.completedAllTasks() || !manager.shouldTerminate()){
            List<Message> messages = manager.receiveMessageFromQueue(APPS_TO_MANAGER_QUEUE);
            for(Message message: messages) {

                if (message.body().equals("terminate")){
                    System.out.println("manager received terminate msg");
                    manager.setTerminate();
                }else if(message.body().length() > 7 && message.body().contains("finished")){
                    System.out.println("manager received finish msg from app");
                    String[] dividedMessage = message.body().split("\n");
                    String appName = dividedMessage[0].substring(4);
                    manager.removeAppFromManager(appName);
                }
                else{
                    String[] dividedMessage = message.body().split("\n");
                    String appName = dividedMessage[0].substring(5);
                    String queueName = dividedMessage[1].substring(11);
                    String bucketName = dividedMessage[2].substring(18);
                    String bucketKey = dividedMessage[3].substring(15);
                    System.out.println("manager received msg from app:" + appName);

                    manager.addInputBucketForApp(appName, bucketName);
                    manager.createQueue(appName, queueName);
                    System.out.println("manager - created queue fpr app:" + appName);

                    manager.addResultsQueueForApp(appName);
                    manager.openThreadForApp(appName, bucketName, bucketKey);
                }
                manager.deleteMsgFromQueue(APPS_TO_MANAGER_QUEUE, message);
            }
        }
        manager.terminate();
    }
    }

