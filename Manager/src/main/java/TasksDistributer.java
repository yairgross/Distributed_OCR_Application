public class TasksDistributer implements Runnable {

    Manager manager;
    String appName;
    String bucketName;
    String bucketKey;

    public TasksDistributer(Manager manager, String appName, String bucketName, String bucketKey){
        this.manager = manager;
        this.appName = appName;
        this.bucketName = bucketName;
        this.bucketKey = bucketKey;
    }

    @Override
    public void run() {
        //downloads the file and modifies number of workers and messages, adds workers if needed
        String[] urlsArray = manager.downloadFileFromS3(appName, bucketName, bucketKey);
        System.out.println("manager downloaded input file for app:" + appName);
        manager.insertNumOfTasksPerApp(appName, urlsArray.length);
        manager.addWorkersIfNeeded();
        manager.openResultUploaderForApp(appName);

        for(String url: urlsArray){
            String taskText ="app:"+appName+"\n"+"url:"+url;
            manager.sendTaskToWorkersQueue(taskText);
            System.out.println("manager - sent msg to worker");
        }
    }
}
