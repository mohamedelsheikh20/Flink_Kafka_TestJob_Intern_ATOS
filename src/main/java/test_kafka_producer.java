// reference
// https://github.com/codingharbour/kafka-quick-start/blob/master/src/main/java/com/codingharbour/kafka/producer/SimpleKafkaProducer.java
// https://github.com/apache/flink/blob/master/flink-walkthroughs/flink-walkthrough-common/src/main/java/org/apache/flink/walkthrough/common/entity/Transaction.java

// Kafka Libraries
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
//import org.apache.kafka.common.errors.SerializationException;
//import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Transaction helper class
import org.apache.flink.walkthrough.common.entity.Transaction;

// Convert timestamp string into long
//import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// add kafka properties
//import java.util.Map;
import java.util.Properties;

//// test Avro
//import org.apache.avro.Schema;
//import org.apache.avro.generic.GenericData;
//import org.apache.avro.generic.GenericRecord;
//import org.apache.avro.io.*;
//import org.apache.avro.specific.SpecificDatumWriter;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;


public class test_kafka_producer {
    public static void main(String[] args) {
        //create kafka producer with some properties
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // the Producer which will contain all the data
        Producer<String, String> producer = new KafkaProducer<>(properties);

//        // test ..........................
//        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, TransactionSerializer.class);
//        Producer<String, Transaction> producer = new KafkaProducer<String, Transaction>(properties);
//        // end test ..........................


        // add transactions samples
        Transaction[] transactions = {
                new Transaction(1, Convert_timestamp_to_long("2023-11-01T09:30:00"), 550.75),
                new Transaction(2, Convert_timestamp_to_long("2023-11-01T11:45:00"), 250.20),
                new Transaction(1, Convert_timestamp_to_long("2023-11-01T09:30:00"), 550.50),
                new Transaction(2, Convert_timestamp_to_long("2023-11-01T11:45:00"), 250.20),
                new Transaction(3, Convert_timestamp_to_long("2023-11-01T14:00:00"), 180.30),
                new Transaction(3, Convert_timestamp_to_long("2023-11-01T16:15:00"), 900.45),
                new Transaction(505, Convert_timestamp_to_long("2023-11-01T18:30:00"), 80.60),
                new Transaction(606, Convert_timestamp_to_long("2023-11-02T08:15:00"), 150.75),
                new Transaction(707, Convert_timestamp_to_long("2023-11-02T10:30:00"), 280.90),
                new Transaction(808, Convert_timestamp_to_long("2023-11-02T12:45:00"), 200.40),
                new Transaction(909, Convert_timestamp_to_long("2023-11-02T15:00:00"), 350.55),
                new Transaction(111, Convert_timestamp_to_long("2023-11-02T17:15:00"), 120.70),
                new Transaction(222, Convert_timestamp_to_long("2023-11-03T09:30:00"), 180.85),
                new Transaction(333, Convert_timestamp_to_long("2023-11-03T11:45:00"), 90.20),
                new Transaction(444, Convert_timestamp_to_long("2023-11-03T14:00:00"), 300.35),
                new Transaction(555, Convert_timestamp_to_long("2023-11-03T16:15:00"), 220.50),
                new Transaction(666, Convert_timestamp_to_long("2023-11-03T18:30:00"), 150.65),
                new Transaction(777, Convert_timestamp_to_long("2023-11-04T08:15:00"), 250.80),
                new Transaction(888, Convert_timestamp_to_long("2023-11-04T10:30:00"), 130.95),
                new Transaction(999, Convert_timestamp_to_long("2023-11-04T12:45:00"), 180.10),
                new Transaction(111, Convert_timestamp_to_long("2023-11-04T15:00:00"), 280.25),
                new Transaction(222, Convert_timestamp_to_long("2023-11-04T17:15:00"), 320.40),
                new Transaction(333, Convert_timestamp_to_long("2023-11-05T09:30:00"), 90.55),
                new Transaction(444, Convert_timestamp_to_long("2023-11-05T11:45:00"), 200.70),
                new Transaction(555, Convert_timestamp_to_long("2023-11-05T14:00:00"), 150.85),
                new Transaction(666, Convert_timestamp_to_long("2023-11-05T16:15:00"), 110.00),
                new Transaction(777, Convert_timestamp_to_long("2023-11-05T18:30:00"), 270.15),
                new Transaction(888, Convert_timestamp_to_long("2023-11-06T08:15:00"), 180.30),
                new Transaction(999, Convert_timestamp_to_long("2023-11-06T10:30:00"), 150.45),
                new Transaction(111, Convert_timestamp_to_long("2023-11-06T12:45:00"), 280.60),
                new Transaction(222, Convert_timestamp_to_long("2023-11-06T15:00:00"), 200.75),
                new Transaction(333, Convert_timestamp_to_long("2023-11-06T17:15:00"), 130.90),
                new Transaction(444, Convert_timestamp_to_long("2023-11-07T09:30:00"), 250.05),
                new Transaction(555, Convert_timestamp_to_long("2023-11-07T11:45:00"), 110.20),
                new Transaction(666, Convert_timestamp_to_long("2023-11-07T14:00:00"), 180.35),
                new Transaction(777, Convert_timestamp_to_long("2023-11-07T16:15:00"), 300.50),
                new Transaction(888, Convert_timestamp_to_long("2023-11-07T18:30:00"), 220.65),
                new Transaction(999, Convert_timestamp_to_long("2023-11-08T08:15:00"), 150.80),
                new Transaction(111, Convert_timestamp_to_long("2023-11-08T10:30:00"), 120.95),
                new Transaction(222, Convert_timestamp_to_long("2023-11-08T12:45:00"), 180.10),
                new Transaction(333, Convert_timestamp_to_long("2023-11-08T15:00:00"), 280.25),
                new Transaction(444, Convert_timestamp_to_long("2023-11-08T17:15:00"), 320.40),
                new Transaction(555, Convert_timestamp_to_long("2023-11-09T09:30:00"), 90.55),
                new Transaction(666, Convert_timestamp_to_long("2023-11-09T11:45:00"), 200.70),
                new Transaction(777, Convert_timestamp_to_long("2023-11-09T14:00:00"), 150.85),
                new Transaction(888, Convert_timestamp_to_long("2023-11-09T16:15:00"), 110.00),
                new Transaction(999, Convert_timestamp_to_long("2023-11-09T18:30:00"), 270.15),
                new Transaction(111, Convert_timestamp_to_long("2023-11-10T08:15:00"), 180.30),
                new Transaction(222, Convert_timestamp_to_long("2023-11-10T10:30:00"), 150.45),
        };

        // Specify the target Kafka topic
        String topic = "transactions";

        // loop in transactions to Produce each record to the Kafka topic
        for (Transaction transaction : transactions) {
//            // test ..........................
//            ProducerRecord<String, Transaction> producerRecord = new ProducerRecord<>(topic, transaction);
//            // end test ..........................
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, transaction.toString());
            producer.send(producerRecord);
        }

        // Close the producer
        producer.close();
    }

    private static long Convert_timestamp_to_long(String str_timestamp) {
        // Convert timestamp into long to match it with the main Transaction class
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(str_timestamp, formatter);

        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }


    // test to create Avro serializer
//    public static class TransactionSerializer implements Serializer<Transaction> {
//        @Override
//        public byte[] serialize(String topic, Transaction transaction) {
//            // Create Avro GenericRecord using the provided schema
//            GenericRecord avroRecord = new GenericData.Record(getSchema());
//            avroRecord.put("accountId", transaction.getAccountId());
//            avroRecord.put("timestamp", transaction.getTimestamp());
//            avroRecord.put("amount", transaction.getAmount());
//
//            // Serialize the GenericRecord to byte array
//            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
//
//            DatumWriter<GenericRecord> datumWriter = new SpecificDatumWriter<>(getSchema());
//            try {
//                datumWriter.write(avroRecord, encoder);
//                encoder.flush();
//                outputStream.close();
//
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//
//            return outputStream.toByteArray();
//        }
//
//        public Schema getSchema() {
//            return new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Transaction\",\"fields\":[{\"name\":\"accountId\",\"type\":\"int\"},{\"name\":\"timestamp\",\"type\":\"long\"},{\"name\":\"amount\",\"type\":\"double\"}]}");
//        }
//    }
}
