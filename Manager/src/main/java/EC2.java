import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;
import java.util.List;

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
                        for (Tag tag : instance.tags()) {
                            if (tag.key().equals("Name")) {
                                if (tag.value().equals("Manager")) {
                                    isManagerUp = true;
                                    break; //todo : should break from entire loop
                                }
                            }
                        }

                    }
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
        String userData = "#!/bin/bash -x\n" +
                "set -e -x\n" +
//                "sudo yum update -y\n"  +
//                "sudo yum install maven -y\n"  +
//                "sudo yum install java-1.8.0-openjdk -y\n" +
                "aws s3api get-object --bucket bucket-programs --key manager.zip /tmp/manager.zip\n" +
                "unzip -P yairpass /tmp/manager.zip\n"  +
                "java -jar /tmp/manager.jar "+ n + "\n";
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

    public String createWorkerInstance(String name, String amiId, int n) {

//        String userData = "#!/bin/bash -x\n" +
//                "set -e -x\n" +
////                "sudo yum update -y\n" +
////                "sudo yum install maven -y\n" +
////                "sudo yum install java-1.8.0-openjdk -y\n" +
//                "aws s3api get-object --bucket bucket-ttest --key worker.zip /tmp/worker.zip\n" +
//                "unzip -P yairpass /tmp/worker.zip\njava -jar /tmp/worker.jar" + n + "\n";

        String userData = "#!/bin/bash -x\n"+ "sudo yum check-update -y\n"+
                "aws s3api get-object --bucket bucket-programs --key worker.zip /tmp/worker.zip\n"+
                "cd /tmp\n"+
                "unzip -P yairpass worker.zip\n"+
                "java -jar worker.jar "+n+"\n";

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

    public void terminateEC2(String instanceID) {
        try{
            TerminateInstancesRequest ti = TerminateInstancesRequest.builder()
                    .instanceIds(instanceID)
                    .build();

            TerminateInstancesResponse response = ec2Client.terminateInstances(ti);
            List<InstanceStateChange> list = response.terminatingInstances();
            for (InstanceStateChange sc : list) { //todo delete
                System.out.println("The ID of the terminated instance is " + sc.instanceId());
            }

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
}