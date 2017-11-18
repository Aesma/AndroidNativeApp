package com.example.rengar.pedometer;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.os.Build;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.content.Context;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    float[] oriValues = new float[3];//三轴数据
    final int valueNum = 4;
    float[] tempValue = new float[valueNum]; //用于存放计算阈值的波峰波谷差值
    boolean lastStatus = false;//上一点的状态，上升还是下降
    int tempCount = 0;
    int continueUpCount = 0;//持续上升次数
    int continueUpFormerCount = 0;//上一点的持续上升的次数，为了记录波峰的上升次数
    boolean isDirectionUp = false;//是否上升的标志位
    long timeOfThisPeak = 0;//此次波峰的时间
    long timeOfLastPeak = 0;//上次波峰的时间
    long timeOfNow = 0;//当前的时间
    float peakOfWave = 0; //波峰值
    float valleyOfWave = 0;  //波谷值
    float gravityNew = 0;  //当前传感器的值
    float gravityOld = 0;//上次传感器的值
    final float initialValue = (float) 1.3;//动态阈值需要动态的数据，这个值用于这些动态数据的阈值
    float ThreadValue = (float) 2.0; //初始阈值

    private Button resetButton;
    private Button writeButton;
    private TextView textView;

    private LineChart mLineChart;
    private List<Entry> accXEntries;
    private List<Entry> accYEntries;
    private List<Entry> accZEntries;
    private List<Entry> accLEntries;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        resetButton = (Button)findViewById(R.id.button);
        writeButton = (Button)findViewById(R.id.button2);
        textView = (TextView)findViewById(R.id.textView2);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("0");
            }
        });
        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile();
            }
        });
        //============================================================================
        mLineChart = (LineChart) findViewById(R.id.chart);
        accXEntries = new ArrayList<>();
        accYEntries = new ArrayList<>();
        accZEntries = new ArrayList<>();
        accLEntries = new ArrayList<>();
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        count++;
        accXEntries.add(new Entry(count, event.values[0]));
        accYEntries.add(new Entry(count, event.values[1]));
        accZEntries.add(new Entry(count, event.values[2]));
        for (int i = 0; i < 3; i++) {
            oriValues[i] = event.values[i];
        }
        gravityNew = (float) Math.sqrt(oriValues[0] * oriValues[0] + oriValues[1] * oriValues[1] + oriValues[2] * oriValues[2]);
        accLEntries.add(new Entry(count, gravityNew));
        if (accXEntries.size() > 150) {
            accXEntries.remove(0);
            accYEntries.remove(0);
            accZEntries.remove(0);
            accLEntries.remove(0);
        }
        LineDataSet accXLineDataSet = new LineDataSet(accXEntries, "accX");
        accXLineDataSet.setColor(Color.RED);
        accXLineDataSet.setCircleColor(Color.RED);

        LineDataSet accYLineDataSet = new LineDataSet(accYEntries, "accY");
        accYLineDataSet.setColor(Color.GREEN);
        accYLineDataSet.setCircleColor(Color.GREEN);

        LineDataSet accZLineDataSet = new LineDataSet(accZEntries, "accZ");
        accZLineDataSet.setColor(Color.BLUE);
        accZLineDataSet.setCircleColor(Color.BLUE);
        LineDataSet accLLineDataSet = new LineDataSet(accLEntries, "accL");
        accLLineDataSet.setColor(Color.YELLOW);
        accLLineDataSet.setCircleColor(Color.YELLOW);

         List<ILineDataSet> dataSets = new ArrayList<>();
         dataSets.add(accXLineDataSet);
         dataSets.add(accYLineDataSet);
         dataSets.add(accZLineDataSet);
         dataSets.add(accLLineDataSet);
         LineData lineData = new LineData(dataSets);
         DetectorNewStep(gravityNew);

         mLineChart.setData(lineData);
         mLineChart.invalidate();
    }

    public void DetectorNewStep(float values) {
        if (gravityOld == 0) {
            gravityOld = values;
        }else {
            if(DetectorPeak(values, gravityOld)){
                timeOfLastPeak = timeOfThisPeak;
                timeOfNow = System.currentTimeMillis();
                if((timeOfNow - timeOfLastPeak >= 200) && (peakOfWave - valleyOfWave >= ThreadValue)){
                    timeOfThisPeak = timeOfNow;
                    int count = Integer.parseInt(textView.getText().toString());
                    textView.setText(new Integer(count + 1).toString());
                }
                if((timeOfNow - timeOfLastPeak >= 200) && (peakOfWave - valleyOfWave >= initialValue)){
                    timeOfThisPeak = timeOfNow;
                    ThreadValue = Peak_Valley_Thread(peakOfWave - valleyOfWave);
                }
            }
        }
        gravityOld = values;
    }

    public boolean DetectorPeak(float newValue, float oldValue){
        lastStatus = isDirectionUp;
        if(newValue > oldValue){
            isDirectionUp = true;
            continueUpCount++;
        }else {
            continueUpFormerCount = continueUpCount;
            continueUpCount = 0;
            isDirectionUp = false;
        }
        if (!isDirectionUp && lastStatus && (continueUpFormerCount >= 2 || oldValue >= 20)) {
            peakOfWave = oldValue;
            return true;
        }else if (!lastStatus && isDirectionUp) {
            valleyOfWave = oldValue;
            return false;
        }else {
            return false;
        }
    }

    public float Peak_Valley_Thread(float value) {
        float tempThread = ThreadValue;
        if(tempCount < valueNum){
            tempValue[tempCount] = value;
            tempCount++;
        }else {
            tempThread = averageValue(tempValue, valueNum);
            for (int i = 1; i < valueNum; i++) {
                tempValue[i - 1] = tempValue[i];
            }
            tempValue[valueNum - 1] = value;
        }
        return tempThread;
    }

    public float averageValue(float value[], int n) {
        float ave = 0;
        for (int i = 0; i < n; i++) {
            ave += value[i];
        }
        ave = ave / valueNum;
        if (ave >= 8)
            ave = (float) 4.3;
        else if (ave >= 7 && ave < 8)
            ave = (float) 3.3;
        else if (ave >= 4 && ave < 7)
            ave = (float) 2.3;
        else if (ave >= 3 && ave < 4)
            ave = (float) 2.0;
        else {
            ave = (float) 1.3;
        }
        return ave;
    }

    private void writeFile(){
        String message = new String();
        DecimalFormat df = new DecimalFormat("#,##0.000");
        message = df.format(oriValues[0]) + " ";
        message += df.format(oriValues[1]) + " ";
        message += df.format(oriValues[2]) + " ";
        message += df.format(gravityNew) + "\n";
        try {
            File file = new File("/Pedometer/data.txt");
            if (!file.exists()) {
                file.createNewFile();
                RandomAccessFile randomFile = new RandomAccessFile("/Pedometer/data.txt", "rw");
// The length of the file (the number of bytes)
                long fileLength = randomFile.length();
// Move the file pointer to the end of the file
                randomFile.seek(fileLength);
                randomFile.writeBytes(message);
                randomFile.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
