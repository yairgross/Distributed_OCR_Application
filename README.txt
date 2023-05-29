Submitters:
	Sharon Hendy 209467158
	Yair Gross 314625781

Instructions to run the project:
	create jars and zip files that contain the jars:
	1. cd ./Worker
	2. mvn package
	3. cd target
	4. mv worker-jar-with-dependencies.jar worker.jar
	5. zip worker.zip worker.jar -p yairpass
	6. cd ./Manager
	7. mvn package 
	8. cd target
	9. mv manager-jar-with-dependencies.jar manager.jar
	10.zip manager.zip manager.jar -p yairpass
	
	create a bucket in S3 with the name "bucket-programs" in region: us-east-1
	and upload both zip files to the bucket:
	11.aws s3api create-bucket --bucket bucket-programs --region us-east-1
	12.aws s3 cp ./Worker/target/worker.zip s3://bucket-programs/
	13.aws s3 cp ./Manager/target/manager.zip s3://bucket-programs/
	
	create a jar for the application and run the program:
	14.cd ./Application_OCR
	15.mvn package
	16.cd target
	17.mv Application_OCR-jar-with-dependencies.jar application_OCR.jar
	18.java -jar application_OCR.jar <input_file_path> <output_file_path> <n> terminate
	(terminate is optional)
	
	
How our program works:
	The Local Application:
	- Our project is composed of 3 parts: the local application, the manager and the workers.
	- Once a new application is run, it checks whether a manager instance has been created, and if not it sends a request to create one.
	- The application uploads an input file to S3 and sends a message to the manager (via an SQS queue) which contains the name of the bucket. Then it waits for a result.
	- Once received the result, the application creates an HTML file and sends a finishing message to the manager.
	- If the application received a terminate instruction from the user, it sends a terminate message to the manager.
	The Manager:
	- The manager when initiated, creates SQS queues:
		* One for receiving messages from the applications.
		* One for sending messages to the workers.
		* One for receiving messages from the workers.
	- The manager uses an Executor Service to manage several threads:
		* AppsQueueListener: Waits and reads messages from the applications, and creates TasksDisributer thread per application.
		* WorkersQueueListener: Waits and reads messages from the workers, puts each message in its application's results queue in the manager. 
		* TasksDisributer: Downloads the input file from S3, creates messages for each url and sends them to the Workers.
		* ResultUploaderPerApp: waits for result messages for the application, uploads the results to S3, and sends a finishing message to the application when all it's urls are processed.
	- The manager keeps track of the number of tasks that currently need to be handled, and creates workers instances accordingly.
	- The manager keeps track of the number of instances created and makes sure not to step over the limit.
	- If the manager receives a termination message, it initiates a termination process.
	The Worker:
	- Waits for messages with urls from the manager.
	- Performs OCR.
	- Sends the results to the manager.
	- If the workers gets an error message, it sends it to the manager.
	
Our program's instances types:
	- Manager: ami-03c2d807fa9f967c6, Linux with java 1.8 installed,  type T2_MICRO.
	- Worker: ami-0e82ae023db057ca1, Linux with java 1.8 and tesseract installed, type T2_MICRO.
	
Running time: While running our program with n=5 given the example input file (and there is no running instance of the Manager before start of execution), the program finished after 2 minutes and 27 seconds.

Security:
	- The zip files containing the jar files for the manager and worker, were encrypted with a password.
	- When creating an EC2 instance, we provided a key and specified an ianInstanceProfile.

Scalablity:
	- The manager works in a thread per client pattern and manages them with an Executor Service. This enables the program to run efficiently with multiple applications.
	- The manager handles tasks from multiple application simultaneously.

Persistence:
	- Workers delete messages from the queue of tasks only after they are done processing them. Thus, if a worker fails while processing a message, another worker will handle it.
	- All Workers receive messages from the same queue. This enhances concurrency and helps the workers process the tasks more efficiently.
	
Termination Process:
	Upon receiving a termination message from an application, the manager terminates all the worker, deletes all the queues and buckets, and shuts itself down.
	