//import com.sun.corba.se.spi.orbutil.threadpool.Work;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;

public class Worker{
    int numOfTasksHandled = 0;
    int n;
    Sqs sqs;
    SqsClient sqsClient;
    String MANAGER_TO_WORKERS_QUEUE = "manager-to-workers-queue";
    String WORKERS_TO_MANAGER_QUEUE = "workers-to-manager-queue";
    Tesseract tess;
    String name = "worker:" + new Date().getTime();

    public Worker(int n){
        this.n = n;
        this.sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
        this.sqs = new Sqs(sqsClient);
        tess = new Tesseract();
        tess.setDatapath("/usr/share/tesseract/tessdata");
    }

    public void start(){
        while(true){
            List<Message> messages;
            try {
                messages = sqs.receiveOneMessageFromQueue(MANAGER_TO_WORKERS_QUEUE);

                for(Message message: messages){
                    String[] dividedMessage = message.body().split("\n");
                    String appName = dividedMessage[0].substring(4);
                    String imgUrl = dividedMessage[1].substring(4);

                    String msg;
                    try{
                        URL url = new URL(imgUrl);
                        BufferedImage img = ImageIO.read(url);
                        String text = tess.doOCR(img);

                        msg = "name:" + appName+"\n" + "imageUrl:" + imgUrl + "\n"+ "text:" + text;
                    }catch (TesseractException | IOException e){
                        msg = "name:" + appName+"\n" + "imageUrl:" + imgUrl + "\n"+ "error:" + e.getMessage();
                    }
                    sqs.sendMessageToQueue(WORKERS_TO_MANAGER_QUEUE, msg);

                    sqs.deleteMessageFromQueue(MANAGER_TO_WORKERS_QUEUE, message);
                    numOfTasksHandled++;
                    System.out.println("worker "+ name + "processed msg, number of tasks handled by worker:" + numOfTasksHandled);
                }
            }catch (QueueDoesNotExistException e){
                System.out.println("manager to workers queue does not exist, stopping.");
                break;
            }
        }
//        System.out.println("worker "+ name + "is shutting down.");
//        try {
//            Runtime.getRuntime().exec("sudo shutdown -h now"); //shutdown after 5 seconds
//        } catch (IOException e) {
//            System.out.println("error - worker could not terminate");
//        }

    }
}
