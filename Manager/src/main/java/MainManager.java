public class MainManager {

    public static void main(String[] args) {
        Manager manager = new Manager(Integer.parseInt(args[0]));
        manager.start();
    }
}
