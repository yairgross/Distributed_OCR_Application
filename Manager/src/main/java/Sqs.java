import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;

public class Sqs {
    SqsClient sqs;

    public Sqs(SqsClient sqs){
        this.sqs = sqs;
    }

    /*
    create a SQS queue with this name.
     */
    public void createQueue(String QueueName){
        try {
            Map<QueueAttributeName, String> map = new HashMap<>();
            map.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20");

            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(QueueName)
                    .attributes(map)
                    .build();
            CreateQueueResponse create_result = sqs.createQueue(request);

        } catch (QueueNameExistsException e) {
            throw e;

        }
    }

    /*
    send a message to the queue.
     */
    public void sendMessageToQueue(String queueName, String message){
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();

        SendMessageRequest send_msg_request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
            //    .delaySeconds(5)
                .build();
        sqs.sendMessage(send_msg_request);
    }

    /*
    receive messages from the queue.
     */
    public List<Message> receiveMessageFromQueue(String queueName){
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        return messages;
    }

    public List<Message> receiveOneMessageFromQueue(String queueName){
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();

        return messages;
    }

    public void deleteMessageFromQueue(String queueName, Message msg){
        try{
            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();
            String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();

            DeleteMessageRequest delMsgReq = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build();
            sqs.deleteMessage(delMsgReq);
        }catch (SqsException e){

        }
    }

    public void deleteQueue(String queueName){
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();
        DeleteQueueRequest deleteReq = DeleteQueueRequest.builder().queueUrl(queueUrl).build();
        DeleteQueueResponse deleteRes = sqs.deleteQueue(deleteReq);
    }
}
