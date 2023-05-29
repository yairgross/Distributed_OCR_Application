import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;

public class EC2 {
    Ec2Client ec2Client;

    public EC2(Ec2Client ec2) {
        this.ec2Client = ec2;
    }

    /*
    checks if there is a manager already.
    */
    public boolean checkIfManagerIsUp() {
        boolean isManagerUp = false;
        String nextToken = null;

        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2Client.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if(instance.state().name() == InstanceStateName.RUNNING ||
                                instance.state().name() == InstanceStateName.PENDING){
                            for (Tag tag : instance.tags()) {
                                if (tag.key().equals("Name")) {
                                    if (tag.value().equals("Manager")) {
                                        isManagerUp = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if(isManagerUp){break;}
                    }
                    if(isManagerUp){break;}
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return isManagerUp;
    }

    /*
    create EC2 instance with a Name tag.
     */
    public String createManagerInstance(String name, String amiId, int n) {
//        String userData = "#!/bin/bash -x\n" +
////                "set -e -x\n" +//todo it terminates the session
////                "sudo add-apt-repository ppa:webupd8team/java -y\n"+ //todo
//                "sudo apt-get update -y\n" +
//                "sudo apt-get install openjdk-8-jdk\n"+//todo
//                "echo hello\n"  +
//                //"sudo yum install maven -y\n"  +
//                "java -version\n";
////                "sudo apt install java-1.8.0-openjdk -y\n";//todo !!!
////                "aws s3api get-object --bucket bucket-programs --key manager.zip /tmp/manager.zip\n"+
////                "unzip -P yairpass /tmp/manager.zip\n"  +
////                "cd /tmp/\n"+
////                "java -jar manager.jar "+ n + "\n";

        String userData = "#!/bin/bash -x\n"+ "sudo yum check-update -y\n"+
                "aws s3api get-object --bucket bucket-programs --key manager.zip /tmp/manager.zip\n"+
                "cd /tmp\n"+
                "unzip -P yairpass manager.zip\n"+
                "java -jar manager.jar "+n+"\n";


        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T2_MICRO)
                .imageId(amiId)
                .maxCount(1)
                .minCount(1)
                .keyName("vockey")
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().arn("arn:aws:iam::092669606190:instance-profile/LabInstanceProfile").build())
                .instanceInitiatedShutdownBehavior("terminate")
                .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
                .build();
        RunInstancesResponse response = ec2Client.runInstances(runRequest);

        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2Client.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 instance %s based on AMI %s",
                    instanceId, amiId);

        } catch (Ec2Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return instanceId;
    }
}