import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;

public class S3 {
    private static S3Client s3;

    public S3(S3Client s3Client){
        s3 = s3Client;
    }

//
public String getAllObjectsInBucket(String bucket) {
    String results = "";
    ListObjectsRequest lstReq = ListObjectsRequest.builder().bucket(bucket).build();
    ListObjectsResponse res = s3.listObjects(lstReq);
    for (S3Object o : res.contents()) {
        results += o.key() + "\t" + getObject(bucket, o.key()) + "\n";
    }
    return results;
}

    public void uploadStrToS3(String bucketName, String key, ByteBuffer str){
        PutObjectRequest req = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        s3.putObject(req, RequestBody.fromByteBuffer(str));
    }

    public void deleteObjectsInBucket (String bucket) {

        try {
            // To delete a bucket, all the objects in the bucket must be deleted first.
            ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build();
            ListObjectsV2Response listObjectsV2Response;

            do {
                listObjectsV2Response = s3.listObjectsV2(listObjectsV2Request);
                for (S3Object s3Object : listObjectsV2Response.contents()) {
                    DeleteObjectRequest request = DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Object.key())
                            .build();
                    s3.deleteObject(request);
                }
            } while (listObjectsV2Response.isTruncated());
        }
        catch(Exception e){}

    }
    public String getObject(String bucketName, String key){
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(key)
                .bucket(bucketName)
                .build();

        ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(objectRequest);
        byte[] data = objectBytes.asByteArray();

        return new String(data, StandardCharsets.UTF_8);
    }

    public void createBucket(String bucket, Region region) {
        s3.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucket)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
//                                .locationConstraint(region.id())
                                .build())
                .build());

        System.out.println(bucket);


    }

    public void deleteBucket(String bucket) {
        DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucket).build();
        s3.deleteBucket(deleteBucketRequest);
    }

    /**
     * Uploading an object to S3 in parts
     */
    public void multipartUpload(String bucketName, String key, List<ByteBuffer> lstOfBuffers) throws IOException {
        // First create a multipart upload and get upload id
        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName).key(key)
                .build();
        CreateMultipartUploadResponse response = s3.createMultipartUpload(createMultipartUploadRequest);
        String uploadId = response.uploadId();
        System.out.println(uploadId);

        CompletedPart[] parts = new CompletedPart[lstOfBuffers.size()];
        int partNum = 1;
        while(!lstOfBuffers.isEmpty()){
            // Upload all the different parts of the object
            UploadPartRequest uploadPartRequest1 = UploadPartRequest.builder().bucket(bucketName).key(key)
                    .uploadId(uploadId)
                    .partNumber(partNum).build();
            String etag1 = s3.uploadPart(uploadPartRequest1, RequestBody.fromByteBuffer(lstOfBuffers.get(0))).eTag();
            CompletedPart part1 = CompletedPart.builder().partNumber(partNum).eTag(etag1).build();
            parts[partNum - 1] = part1;
            lstOfBuffers.remove(0);
            partNum++;
        }

        // Finally call completeMultipartUpload operation to tell S3 to merge all uploaded
        // parts and finish the multipart operation.
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder().parts(parts).build();
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                CompleteMultipartUploadRequest.builder().bucket(bucketName).key(key).uploadId(uploadId)
                        .multipartUpload(completedMultipartUpload).build();
        s3.completeMultipartUpload(completeMultipartUploadRequest);
    }
}
