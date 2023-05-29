public class ResultsUploaderPerApp implements Runnable{
    Manager manager;
    String appName;

    public ResultsUploaderPerApp(Manager manager, String appName){
        this.manager = manager;
        this.appName = appName;
    }

    @Override
    public void run() {
        int numOfTasksHandled = 0;
        while(numOfTasksHandled < manager.getNumOfTasksPerApp(appName)){
            try {
                String[] msg = manager.getResultForApp(appName);
                String imageUrl = msg[0];
                String text = msg[1];

                manager.uploadResultToS3(appName, imageUrl, text);
                System.out.println("manager uploaded result to s3, num of tasks for app left:" + (manager.getNumOfTasksPerApp(appName) - numOfTasksHandled - 1));
                //manager.decreaseTaskCountOfApp(appName);
                manager.updateNumOfMessages(-1);
                numOfTasksHandled++;
            } catch (InterruptedException e) {
                System.out.println("resultUploader for app:" + appName+ "has been interrupted.");
                e.printStackTrace();
            }
        }
        manager.sendFinishMessageToApp(appName);
        System.out.println("manager sent finish msg to app:" + appName);
    }
}
