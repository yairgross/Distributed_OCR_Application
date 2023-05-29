import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

public class WorkersQueueListener implements Runnable {

    Manager manager;
    String WORKERS_TO_MANAGER_QUEUE;


    public WorkersQueueListener(Manager manager, String WORKERS_TO_MANAGER_QUEUE) {
        this.manager = manager;
        this.WORKERS_TO_MANAGER_QUEUE = WORKERS_TO_MANAGER_QUEUE;
    }

    public void run(){
        while(!manager.completedAllTasks() || !manager.shouldTerminate()){
            List<Message> messages = manager.receiveMessageFromQueue(WORKERS_TO_MANAGER_QUEUE);
            for(Message message: messages){
                String[] dividedMessage = message.body().split("\n");
                String appName = dividedMessage[0].substring(5);
                String imageUrl = dividedMessage[1].substring(9);
                String text = dividedMessage[2];

                manager.putResultForApp(appName, new String[]{imageUrl, text});
//                System.out.println("manager received msg from worker");
//                manager.uploadResultToS3(appName, imageUrl, text);
//                System.out.println("manager uploaded result to s3, num of tasks for app left:" + manager.getNumOfTasksPerApp(appName));
//                manager.decreaseTaskCountOfApp(appName);
//                manager.updateNumOfMessages(-1);
//                if(manager.getNumOfTasksPerApp(appName) == 0) {
//                    manager.sendFinishMessageToApp(appName);
////                    manager.removeAppFromManager(appName);
//                    System.out.println("manager sent finish msg to app:" + appName);
//                }
                manager.deleteMsgFromQueue(WORKERS_TO_MANAGER_QUEUE, message);
            }
        }
    }
}
