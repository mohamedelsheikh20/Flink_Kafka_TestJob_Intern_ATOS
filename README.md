# Level 2 Code

## * Build Gradle
### Add dependencies:
* `Normal dependencies needed`
  * testImplementation platform('org.junit:junit-bom:5.9.1')
  * testImplementation 'org.junit.jupiter:junit-jupiter'
  * implementation 'ch.qos.logback:logback-classic:1.2.3'
* `Use the version for Java 8 (Scala 2.11)`
  * implementation 'org.apache.flink:flink-java:1.14.0'
* `Use Streaming`
  * implementation group: 'org.apache.flink', name: 'flink-streaming-java_2.12', version: '1.14.0'
* `Use flink clients`
  * implementation group: 'org.apache.flink', name: 'flink-clients_2.12', version: '1.14.0'
* `Use flink runtime web (to view data in dashboard)`
  * implementation group: 'org.apache.flink', name: 'flink-runtime-web_2.12', version: '1.14.0' // Use the appropriate version
* `Use flink connector link flink to kafka`
  * implementation group: 'org.apache.kafka', name: 'kafka-clients', version: '2.8.0'
  * implementation group: 'org.apache.flink', name: 'flink-connector-kafka_2.12', version: '1.14.0'
* `Use flink walkthrough library`
  * implementation group: 'org.apache.flink', name: 'flink-walkthrough-common_2.12', version: '1.14.0'

---

## * Add Kafka Producer to add data into kafka using java
### * Import needed libraries
### * Function `Convert_timestamp_to_long`: 
   * function used to convert `str timestamp` to `long datetime` 
### * class `test_kafka_producer`:
* the main class which will be run to produce data into kafka.
  * method `Main`:
    * set properties to create kafka producer (Broker, Key and Value).
    ```
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    ```
    * add new producer.
    ```
    Producer<String, String> producer = new KafkaProducer<>(properties);
    ```
    * set transactions in `Transaction` Array with some samples.
    ```
    Transaction[] transactions = {
       new Transaction(101, Convert_timestamp_to_long("2023-11-01T09:30:00"), 150.75),
       new Transaction(202, Convert_timestamp_to_long("2023-11-01T11:45:00"), 250.20),
                                ........
    ```
    * Specify the target Kafka topic to put data into
    ```String topic = "transactions";```
    * loop in transactions to Produce each record to the Kafka topic
    ```
     for (Transaction transaction : transactions){
         ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, transaction.toString()); 
         producer.send(producerRecord);
     }
    ```
    * Finally, we close the producer ```producer.close();```
---

## flink job java
### * Import needed libraries
### * Class `StrToTransactionMapFunction`:
* class that implements `MapFunction` used when we get data from source.
* this class convert the string into `Transaction` class.
* it takes string like `Transaction{accountId=222, timestamp=1699612200000, amount=150.45}` and
  convert it into Transaction `Transaction(accountId, timestamp, amount)`.
  ```
  public static class StrToTransactionMapFunction implements MapFunction<String, Transaction> {
      public Transaction map(String TransactionString) throws Exception {
          TransactionString = TransactionString.substring(13, TransactionString.length() - 1);
          String[] keyValuePairs = TransactionString.split(", ");

          int accountId = Integer.parseInt(keyValuePairs[0].split("=")[1]);
          long timestamp = Long.parseLong(keyValuePairs[1].split("=")[1]);
          double amount = Double.parseDouble(keyValuePairs[2].split("=")[1]);

          return new Transaction(accountId, timestamp, amount);
      }
  }
  ```

### * Class `TransactionWatermarkGenerator`:
* class that implements `WatermarkGenerator` used when we assign `assignTimestampsAndWatermarks`.
* this class set the timestamp of transaction into watermark.
  ```
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
  ```

### * Function extractTimestamp
  * function that takes string event and return only the timestamp.
  * used in watermark to assign the right timestamp.
  ```
    private static long extractTimestamp(String event) {
    try {
        int startIndex = event.indexOf("timestamp=") + "timestamp=".length();
        int endIndex = event.indexOf(",", startIndex);
        String timestampStr = event.substring(startIndex, endIndex).trim();

        return Long.parseLong(timestampStr);
    } catch (Exception e) {
        return -1; // Return a negative value if extraction fails
    }
  }
  ```

### * Class `test_flink_job`
 * the main class which will be run the main job code
   * Method `Main`:
     * Get all parameters from args ```ParameterTool params = ParameterTool.fromArgs(args);```.
     * Get specific parameter and put a default value to it 
        ```
        double transactionLimit = params.getDouble("transaction_limit", 1000.0);
        ```
     * Set the Env ```StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();```.
     * Build kafka source connection from broker `localhost:9092` and topic `transactions` with the earliest starting offset and string schema deserializer.
       ```
       KafkaSource<String> source_transactions = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("transactions")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
       ```
     * Build watermark strategy to set the event time using timestamp of transaction.
       ```
        WatermarkStrategy<String> watermarkStrategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, timestamp) -> {
                    long extractedTimestamp = extractTimestamp(event);
                    return extractedTimestamp > 0 ? extractedTimestamp : timestamp;
        });
       ```
     * Get the data from kafka source using the created watermark strategy and class `StrToTransactionMapFunction`.
       ```
       DataStream<Transaction> transactions_kafka = env
             .fromSource(
                  source_transactions,
                  watermarkStrategy,
                  "Kafka Source"
             )
                .map(new StrToTransactionMapFunction());
       ```
     * Try simple sink data into kafka new topic after creating it using (try - catch).
       * Do simple sum of amount on the Transaction returned data from kafka to test sink it into kafka.
         ```
         DataStream<String> SumDataStream = transactions_kafka
                    .keyBy(Transaction::getAccountId)
                    .sum("amount")
                    .map(Transaction::toString);
         ```
       * Build kafka sink to put data into kafka.
          ```
            KafkaSink<String> sink = KafkaSink.<String>builder()
                    .setDeliverGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                    .setBootstrapServers("localhost:9092")
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic("AggSumTransactions")
                            .setValueSerializationSchema(new SimpleStringSchema())
                            .build()
                    )
                    .build();
          ```
       * sink data into kafka ``` SumDataStream.sinkTo(sink); ```.
     * Do Transaction limit detector on the Transaction returned data from kafka (using complex event `class: test_fraud_detector`).
       ```
        DataStream<String> alerts = transactions_kafka
                .keyBy(Transaction::getAccountId)
                .process(new test_fraud_detector(transactionLimit))
                .name("fraud-detector");
       ```
       * Do Transaction limit detector on the Transaction returned data from kafka (using windowing method) (`...........STILL NOT WORKING...........`).
         ```
           DataStream<Alert> alerts2 = transactions_kafka
                  .assignTimestampsAndWatermarks(WatermarkStrategy
                          .forGenerator(context -> new TransactionWatermarkGenerator())
                          .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                  )
                  .keyBy(Transaction::getAccountId)
                  .window(TumblingEventTimeWindows.of(Time.days(1)))
                  //.sum("amount")  // check if windowing is working using simple sum (also not working)
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
         ```
       * Print data to check if all working well (alerts - alerts2) and print test if the timestamp with watermark is working well.
         ```
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
         ```
       * Finally, Execute the Env ```env.execute("transactions Example");```.
---

## Complex Event Java Class
### * Class `test_fraud_detector` that extends the `KeyedProcessFunction`
#### * Import needed libraries

#### * Function `isSameDay`:
* function that compare two timestamps together.
* this function return `True` if the two timestamps are in the same day else return `False`.
  ```
  private static boolean isSameDay(long timestamp1, long timestamp2) {
        LocalDateTime dateTime1 = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp1), ZoneId.systemDefault());
        LocalDateTime dateTime2 = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp2), ZoneId.systemDefault());

        return dateTime1.toLocalDate().isEqual(dateTime2.toLocalDate());
    }
  ```

#### * Function `convertToReadableTimestamp`:
* function that takes long timestamp and convert it into readable string.
* this function return `Date with time` to check time when the account make exceeded transaction.
  ```
  private static String convertToReadableTimestamp(long timestamp) {
        // Returns the timestamp in ISO_LOCAL_DATE_TIME format (e.g., 2023-11-14T12:30:45)
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.toString();
    }
  ```

#### * Function `convertToReadableDate`:
* function that takes long timestamp and convert it into readable string.
* this function return `Date only` to check date of the sum of all previous transactions.
  ```
  private static String convertToReadableDate(long timestamp) {
        LocalDate date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).toLocalDate();
        return date.toString(); // Returns the date in ISO_LOCAL_DATE format (e.g., 2023-11-14)
    }
  ```

#### * the `main class` which will be run the main job code
* Declare needed variables:
  * `double TRANSACTION_LIMIT`: the limit of transaction which will be passed from (conf or arg).
  * `ValueState<Double> dailyTransactionTotal`: double which we will sum the whole transactions amount.
  * `ValueState<Long> dailyShift`: check if we are at the same day or the day are the next one.

* Constructor to get the limit value (Args or conf)
  ```
  public test_fraud_detector(double transactionLimit)
    {TRANSACTION_LIMIT = transactionLimit;}
  ```

* Set the configuration of the app (dailyTransactionTotal "sum of amounts") / (dailyShift "same day or not")
  ```
    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<Double> DailyTransDescriptor =
                new ValueStateDescriptor<>("daily-trans-state", Types.DOUBLE);
        dailyTransactionTotal = getRuntimeContext().getState(DailyTransDescriptor);

        ValueStateDescriptor<Long> DailyShiftDescriptor =
                new ValueStateDescriptor<>("daily-shift-state", Types.LONG);
        dailyShift = getRuntimeContext().getState(DailyShiftDescriptor);
    }
  ```

* Process each transaction (element) arrive
  * first override the main `processElement` function.
    ```  
    @Override
    public void processElement(Transaction transaction, Context context, Collector<String> collector) throws Exception {
    ```
  
  * create ```double currentTotal;``` variable to save the current amount of the current transaction.
  
  * if the value of timestamp `dailyShift` is not null so we check if this transaction is in the next day of the previous 
       transaction, so we send message of the sum of previous day transactions.
    * then we update `dailyShift` with the new timestamp of the new day.
    * finally, we clear all previous day transactions which is in `dailyTransactionTotal`.
    ```
          // if dailyShift is not null and if we in the same day
          if (dailyShift.value() != null)
          {
              // if we not in the same day
              if (!isSameDay(dailyShift.value(), transaction.getTimestamp()))
              {
                  // show the transaction per the whole day even if it not exceed the limit
                  String output = "User with account: " + transaction.getAccountId() + " is make transactions " +
                          dailyTransactionTotal.value() + " in day: " + convertToReadableDate(transaction.getTimestamp());
                  collector.collect(output);

                  // output here is the date only of the time stamp and the id of user and the amount of dailyTransactionTotal
                  dailyShift.update(transaction.getTimestamp());
                  dailyTransactionTotal.clear();
              }
              // if we in the same day do nothing
          }
    ```
  * if the timestamp `dailyShift` is null so that's mean it is the first value, so we update it with the first time comes.
  ```
        else {
            // if dailyShift it null
            dailyShift.update(transaction.getTimestamp());
            dailyTransactionTotal.clear();
        }
  ```

  * now we check amount transaction `dailyTransactionTotal` to add the new data and update it `dailyTransactionTotal` 
    using temp variable called `currentTotal` 
    ```
      // check amount transaction to sum
         if (dailyTransactionTotal.value() != null) {
                currentTotal = dailyTransactionTotal.value();
         }
         else {
                currentTotal = 0.0;
         }
         currentTotal += transaction.getAmount();
         dailyTransactionTotal.update(currentTotal);
    ```
    * finally, check the limit `TRANSACTION_LIMIT` and print message if it exceeded.
    ```
            if (currentTotal > TRANSACTION_LIMIT) {
                String output = "User with account: " + transaction.getAccountId() + " is exceeded limit 1000 in day: " +
                        convertToReadableTimestamp(transaction.getTimestamp());
                collector.collect(output);
            }
        }
    ```



---
## Output Shape:
![output shape](https://github.com/mohamedelsheikh20/Flink_Kafka_TestJob_Intern_ATOS/assets/65075222/b2b0c37d-c619-4ed3-b049-964f13155819)

* show each account Id transactions at the end of the day.
* show immediatly if account is exceeded certain amount.
