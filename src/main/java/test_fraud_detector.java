import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.walkthrough.common.entity.Transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;

public class test_fraud_detector extends KeyedProcessFunction<Long, Transaction, String> {
    private static final long serialVersionUID = 1L;
    private final double TRANSACTION_LIMIT;
    private transient ValueState<Double> dailyTransactionTotal;
    private transient ValueState<Long> dailyShift;

    public test_fraud_detector(double transactionLimit)  // Constructor to get the limit value (Args or conf)
    {TRANSACTION_LIMIT = transactionLimit;}

    @Override // Set the configuration of the app (dailyTransactionTotal "sum of amounts") / (dailyShift "same day or not")
    public void open(Configuration parameters) {
        ValueStateDescriptor<Double> DailyTransDescriptor =
                new ValueStateDescriptor<>("daily-trans-state", Types.DOUBLE);
        dailyTransactionTotal = getRuntimeContext().getState(DailyTransDescriptor);

        ValueStateDescriptor<Long> DailyShiftDescriptor =
                new ValueStateDescriptor<>("daily-shift-state", Types.LONG);
        dailyShift = getRuntimeContext().getState(DailyShiftDescriptor);
    }

    @Override  // Process each element arrive
    public void processElement(Transaction transaction, Context context, Collector<String> collector) throws Exception {
        double currentTotal;

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
        else {
            // if dailyShift it null
            dailyShift.update(transaction.getTimestamp());
            dailyTransactionTotal.clear();
        }


        // check amount transaction to sum
        if (dailyTransactionTotal.value() != null) {
            currentTotal = dailyTransactionTotal.value();
        }
        else {
            currentTotal = 0.0;
        }

        currentTotal += transaction.getAmount();
        dailyTransactionTotal.update(currentTotal);

        if (currentTotal > TRANSACTION_LIMIT) {
            String output = "User with account: " + transaction.getAccountId() + " is exceeded limit 1000 in day: " +
                    convertToReadableTimestamp(transaction.getTimestamp());
            collector.collect(output);
        }
    }

    private static boolean isSameDay(long timestamp1, long timestamp2) {
        LocalDateTime dateTime1 = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp1), ZoneId.systemDefault());
        LocalDateTime dateTime2 = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp2), ZoneId.systemDefault());

        return dateTime1.toLocalDate().isEqual(dateTime2.toLocalDate());
    }

    private static String convertToReadableTimestamp(long timestamp) {
        // Returns the timestamp in ISO_LOCAL_DATE_TIME format (e.g., 2023-11-14T12:30:45)
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.toString();
    }

    private static String convertToReadableDate(long timestamp) {
        LocalDate date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).toLocalDate();
        return date.toString(); // Returns the date in ISO_LOCAL_DATE format (e.g., 2023-11-14)
    }
}