import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.walkthrough.common.entity.Alert;

// add parameters (set data into the run commands)
import org.apache.flink.api.java.utils.ParameterTool;

// Use Transaction library
import org.apache.flink.walkthrough.common.entity.Transaction;

// java libraries
import java.time.Duration;



public class test_flink_job {

    public static void main(String[] args) throws Exception {
        // try to use parameter as a configuration to be run from configuration (first option)
//        ParameterTool params = ParameterTool.fromPropertiesFile("path/to/config.properties");

        // using parameters in arguments
        ParameterTool params = ParameterTool.fromArgs(args);

        // Get the transaction limit from command line arguments (Default value is 1000.0)
        double transactionLimit = params.getDouble("transaction_limit", 1000.0);

        // set the environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // build kafka source connection
        KafkaSource<String> source_transactions = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("transactions")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();


        // build watermark strategy to set the event time
        WatermarkStrategy<String> watermarkStrategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, timestamp) -> {
                    long extractedTimestamp = extractTimestamp(event);
                    return extractedTimestamp > 0 ? extractedTimestamp : timestamp;
                });

//         get the data from kafka source
        DataStream<Transaction> transactions_kafka = env
                .fromSource(
                        source_transactions,
                        watermarkStrategy,
                        "Kafka Source"
                )
                .map(new StrToTransactionMapFunction());


        // "Print" check if the output have the right timestamp
        transactions_kafka
                .map(transaction -> {
                    long eventTime = transaction.getTimestamp();
                    return "Transaction: " + transaction + ", Event Time: " + eventTime;
                })
                .print();


        // try sink data into kafka new topic after creating it
        try {
            // do simple sum of amount on the Transaction returned data from kafka to test putting it into kafka
            DataStream<String> SumDataStream = transactions_kafka
                    .keyBy(Transaction::getAccountId)
                    .sum("amount")
                    .map(Transaction::toString);


            // build kafka sink to put data into kafka
            KafkaSink<String> sink = KafkaSink.<String>builder()
                    .setDeliverGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                    .setBootstrapServers("localhost:9092")
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic("AggSumTransactions")
                            .setValueSerializationSchema(new SimpleStringSchema())
                            .build()
                    )
                    .build();

            // sink data into kafka
            SumDataStream.sinkTo(sink);


        } catch (Exception e) {
            // Handle the exception here
            e.printStackTrace(); // Or log the exception or perform any other handling
        }


        // do Transaction limit detector on the Transaction returned data from kafka (using complex event)
        DataStream<String> alerts = transactions_kafka
                .keyBy(Transaction::getAccountId)
                .process(new test_fraud_detector(transactionLimit))
                .name("fraud-detector");


        // do Transaction limit detector on the Transaction returned data from kafka (using windowing method)
        DataStream<Alert> alerts2 = transactions_kafka
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .forGenerator(context -> new TransactionWatermarkGenerator())
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                )
                .keyBy(Transaction::getAccountId)
                .window(TumblingEventTimeWindows.of(Time.days(1)))
//                .sum("amount")  // check if windowing is working using simple sum (also not working)
                .process(new ProcessWindowFunction<Transaction, Alert, Long, TimeWindow>() {
                    // test the normal summation after each day
                    private static final long serialVersionUID = 1L;
                    private final double TRANSACTION_LIMIT = 1000;


                    @Override
                    public void process(Long accountId, ProcessWindowFunction<Transaction, Alert, Long, TimeWindow>.Context context, Iterable<Transaction> elements, Collector<Alert> collector) throws Exception {
                        double totalAmount = 0.0;

                        // test the process is working or not
                        System.out.println("process in window function: " + elements);

                        for (Transaction transaction : elements) {
                            totalAmount += transaction.getAmount();

                            if (totalAmount > TRANSACTION_LIMIT) {
                                Alert alert = new Alert();
                                alert.setId(accountId);
                                collector.collect(alert);
                            }
                        }
                    }
                })
                ;


        // Print the result to the console
        alerts.print();
        alerts2.print();


        // test if the timestamp with watermark is working well
        transactions_kafka
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .forGenerator(context -> new TransactionWatermarkGenerator())
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                )
                .process(new ProcessFunction<Transaction, String>() {
                    @Override
                    public void processElement(Transaction transaction, Context ctx, Collector<String> out) throws Exception {
                        long eventTime = transaction.getTimestamp();
                        long currentWatermark = ctx.timerService().currentWatermark();
                        out.collect("Transaction: " + transaction + ", Event Time: " + eventTime + ", Watermark: " + currentWatermark);
                    }
                })
                .print(); // Print the processed elements with their timestamps and watermark


        // Execute the Flink job
        env.execute("transactions Example");
    }


    // Map function str into Transaction type
    public static class StrToTransactionMapFunction implements MapFunction<String, Transaction> {
        // there is no Transaction function to override
        public Transaction map(String TransactionString) throws Exception {
            // input example: Transaction{accountId=222, timestamp=1699612200000, amount=150.45}

            // Remove the prefix "Transaction{" and the suffix "}"
            TransactionString = TransactionString.substring(13, TransactionString.length() - 1);

            // Split the string into an array of key-value pairs
            String[] keyValuePairs = TransactionString.split(", ");


            int accountId = Integer.parseInt(keyValuePairs[0].split("=")[1]);
            long timestamp = Long.parseLong(keyValuePairs[1].split("=")[1]);
            double amount = Double.parseDouble(keyValuePairs[2].split("=")[1]);

            return new Transaction(accountId, timestamp, amount);
        }
    }


    // get the timestamp from the event to send it to watermark
    private static long extractTimestamp(String event) {
        try {
            int startIndex = event.indexOf("timestamp=") + "timestamp=".length();
            int endIndex = event.indexOf(",", startIndex);
            String timestampStr = event.substring(startIndex, endIndex).trim();

            // check if the output is good
//            System.out.println("extractTimestamp function: " + Long.parseLong(timestampStr));
            return Long.parseLong(timestampStr);
        } catch (Exception e) {
//            System.out.println("extractTimestamp fails: ");
            return -1; // Return a negative value if extraction fails
        }
    }


    // using custom watermark generator (bad in production but good for test) instead of "forMonotonousTimestamps"
    public static class TransactionWatermarkGenerator implements WatermarkGenerator<Transaction> {

        @Override
        public void onEvent(Transaction event, long eventTimestamp, WatermarkOutput output) {
            // Emit a watermark based on the timestamp of the Transaction event
            output.emitWatermark(new Watermark(event.getTimestamp()));
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            // don't need to do anything because we emit in reaction to events above
        }
    }
}