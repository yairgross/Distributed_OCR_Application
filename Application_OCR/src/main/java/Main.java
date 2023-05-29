import java.util.Date;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        long time = new Date().getTime();
        boolean terminate = false;
        if(args.length == 4 && args[3].equals("terminate")){
            terminate = true;
        }
       Application app = new Application(args[0], args[1], Integer.parseInt(args[2]), terminate);
       app.start();
        long time1 = new Date().getTime();
       System.out.println("total run time in seconds:" + (time1 - time)/1000);
    }
}
