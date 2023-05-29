
public class MainWorker {
    public static void main(String[] args) {
        Worker worker = new Worker(Integer.parseInt(args[0]));
        worker.start();
    }
}