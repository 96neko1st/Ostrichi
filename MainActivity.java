package com.example.owner.myapplication;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.math.BigDecimal;
import android.icu.text.DecimalFormat;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.UUID;

import static android.R.drawable.ic_menu_manage;


public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener, SensorEventListener, BluetoothAdapter.LeScanCallback {
    /** スピードセンサとの通信 **/

    /**
     * BLE 機器スキャンタイムアウト (ミリ秒)
     **/
    private static final long SCAN_PERIOD = 15000;
    /**
     * 検索機器の機器名"Wahoo BlueSC
     **/
    private static final String DEVICE_NAME = "Wahoo BlueSC";
    /**
     * 対象のサービス UUID(サービスを指定)：Wahoo BlueSC の UUID
     **/
    private static final String DEVICE_BUTTON_SENSOR_SERVICE_UUID = "00001816-0000-1000-8000-00805f9b34fb";
    /**
     * 対象のキャラクタリスティック UUID(通知のキャラクタリスティックを指定)
     **/
    private static final String DEVICE_BUTTON_SENSOR_CHARACTERISTIC_UUID = "00002a5b-0000-1000-8000-00805f9b34fb";
    /**
     * キャラクタリスティック設定 UUID(BluetoothLeGatt プロジェクト、SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG より
     **/
    final int REQUEST_ENABLE_BT = 1; //任意のコード
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private BleStatus mStatus = BleStatus.DISCONNECTED;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;
    private TextView mStatusText, Value;
    private TextView speedText;
    private int QueueNum = 0;
    final int SpeedQueue_Size = 10;
    private double[] SpeedQueue = new double[SpeedQueue_Size];
    //サイコン
    int countUp = 0; //通知毎のカウントアップ
    double speed1 = 0.0;
    BigDecimal resSpeed1;
    int rotationNow = 0;
    int intTimeNow1, intTimeNow2 = 0;
    int intTimeNow = 0;
    int rpc = 0; //rpc:rotation per count １カウントごとののタイヤの回転
    int intTimeDifference = 0;
    double secondHour = 3600.0; //秒→時間
    double tickHour = 1.0 / 1024.0 / secondHour; //タイムスタンプ→時間
    double milliKm = 1.0E-6; //=0.000001
    double constant = milliKm / tickHour; //走行速度計算式(み/じ)
    int SpeedPos = 0;
    int TirePos = 0;
    double LimitSpeed = 5;//速度超過の速度
    int tirePerimeter = 2105; //タイヤ周長(2105mm)
    static final int RESULT_SETINGACTIVITY = 1000;
    int ZeroCount = 0;


    /**
     * BLE 機器を検索する
     **/
    private void connect() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(MainActivity.this);
                if (BleStatus.SCANNING.equals(mStatus)) {
                    setStatus(BleStatus.SCAN_FAILED);
                    reset();
                }
            }
        }, SCAN_PERIOD);
        mBluetoothAdapter.startLeScan(new UUID[]{UUID.fromString(DEVICE_BUTTON_SENSOR_SERVICE_UUID)}, this);
        setStatus(BleStatus.SCANNING);
    }

    /**
     * リセット
     **/
    private void reset() {
        setStatus(BleStatus.RESET);
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            speed1 = 0;
            for (int roop = 0; roop < SpeedQueue_Size; roop++) {
                SpeedQueue[roop] = 0.0;
            }
            QueueNum = 0;
        }
        mBluetoothAdapter.stopLeScan(MainActivity.this);
        countUp = 0;
        connect();
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (DEVICE_NAME.equals(device.getName())) {
            setStatus(BleStatus.DEVICE_FOUND);
            // 省電力のためスキャンを停止する
            //mBluetoothAdapter.stopLeScan(MainActivity.this);
            // GATT 接続を試みる
            mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);
        }
    }


    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // GATT へ接続成功
                // サービスを検索する
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // GATT 通信から切断された
                setStatus(BleStatus.DISCONNECTED);
                reset();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service =
                        gatt.getService(UUID.fromString(DEVICE_BUTTON_SENSOR_SERVICE_UUID));
                if (service == null) {
                    // サービスが見つからなかった
                    setStatus(BleStatus.SERVICE_NOT_FOUND);
                } else {
                    // サービスを見つけた
                    setStatus(BleStatus.SERVICE_FOUND);
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(DEVICE_BUTTON_SENSOR_CHARACTERISTIC_UUID));
                    if (characteristic == null) {
                        // キャラクタリスティックが見つからなかった
                        setStatus(BleStatus.CHARACTERISTIC_NOT_FOUND);
                    } else {
                        // キャラクタリスティックを見つけた
                        // Notification を要求する
                        boolean registered = gatt.setCharacteristicNotification(characteristic, true);
                        // Characteristic の Notification 有効化
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                        if (registered) {
                            // Characteristics 通知設定完了
                            setStatus(BleStatus.NOTIFICATION_REGISTERED);
                        } else {
                            // Characteristics 通知設定失敗
                            setStatus(BleStatus.NOTIFICATION_REGISTER_FAILED);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // READ 成功
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //キャラクタリスティックの値が変化後の処理(サイクルコンピュータとして機能するための処理)
            // Characteristic の値更新通知
            if (DEVICE_BUTTON_SENSOR_CHARACTERISTIC_UUID.equals(characteristic.getUuid().toString())) {
                Byte value = characteristic.getValue()[1]; //ホイールの累積回転数を参照
                Byte time1 = characteristic.getValue()[5]; //タイムスタンプ 1
                Byte time2 = characteristic.getValue()[6]; //タイムスタンプ 2

                //byte型数値→符号なし数値として扱う
                int intValue = value & 0xFF;
                int intTime1 = time1 & 0xFF;
                int intTime2 = time2 & 0xFF;
                intTimeNow1 = intTime1;
                intTimeNow2 = intTime2;
                int rotation = intValue;
                if (countUp == 0) { //最初のカウントの場合
                    rotationNow = rotation;
                    intTimeNow = intTimeNow1 + 256 * intTimeNow2;
                } else { //カウントが 2 回目以降の場合
                    //タイムスタンプの値の差を算出
                    int intTimeBefore = intTimeNow;
                    intTimeNow = intTimeNow1 + 256 * intTimeNow2;
                    if (intTimeBefore != intTimeNow) {
                        if (intTimeBefore <= intTimeNow) {
                            intTimeDifference = intTimeNow - intTimeBefore;
                        } else {//もし intTimeNow が上限(65535)に達したならば
                            intTimeDifference = 65536 - intTimeBefore + intTimeNow;
                        }
                    } else {
                        intTimeDifference = 0;
                    }
                    //countUp,intTime1,2,intTimeNow,intTimeDifference を表示
                    int rotationBefore = rotationNow;
                    rotationNow = rotation;
                    //通知毎のタイヤ回転数(rpc:rotation per count)算出
                    if (rotationBefore < rotationNow) {
                        ZeroCount=0;
                        rpc = rotationNow - rotationBefore;
                    } else if (rotationNow == rotationBefore) { //intValue に変化が無かったら
                        rpc = 0;
                        ZeroCount++;

//                        SpeedQueue[QueueNum] = 0.0;
                    } else if (rotationNow < rotationBefore) { //intValueNow が上限(255)に達したならば
                        ZeroCount=0;
                        rpc = rotationNow + (256 - rotationBefore);
                    }
                    //走行速度算出***********************

                    if (rpc != 0) {//ホイールが回転、スピード計算
                        //constant = milliKm ÷ tickHour | milliKm = 0.000001 | tickHour =1.0 ÷1024.0 ÷ 3600.0
                        SpeedQueue[QueueNum] = tirePerimeter * rpc * constant / intTimeDifference; //タイムスタンプによる計算法(以下タイムスタンプ速度
                    }
                    else if (ZeroCount > SpeedQueue_Size*10) {
                        for (int roop = 0; roop < SpeedQueue_Size; roop++) {
                            SpeedQueue[roop] = 0.0;
                        }
                        speed1 = 0;
                        ZeroCount = 0;
                        QueueNum = 0;
                    }
                    speed1 = Speed_Average(SpeedQueue);
                    QueueNum = NextQueue(QueueNum);

                    //小数点第 1 位
                    BigDecimal bd1 = new BigDecimal(speed1);
                    resSpeed1 = bd1.setScale(1, BigDecimal.ROUND_HALF_UP);
                    //画面更新
                    runOnUiThread(new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void run() {
                            speedText.setText(resSpeed1.toString());
                            speed1 = Speed_Average(SpeedQueue);
                            ImageView image1 = (ImageView) findViewById(R.id.warning1);
                            image1.setImageResource(R.drawable.warning1);
                            //警告画面表示(速度超過)
                            if (speed1 > LimitSpeed) {
                                image1.setVisibility(View.VISIBLE);
                                String message = "B\n";
                                byte[] value2;
                                try {
                                    //send data to service
                                    value2 = message.getBytes("UTF-8");
                                    mService.writeRXCharacteristic(value2);//警告送信
                                    //Update the log with time stamp
                                } catch (UnsupportedEncodingException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                            //警告画面非表示(速度超過)
                            else {
                                image1.setVisibility(View.GONE);
                            }
                        }
                    });
                }
                countUp++; //カウントアップ
            }
        }
    };

    private double Speed_Average(double[] SpeedQueue) {
        double speed;
        double num = 0.0;
        for (int roop = 0; roop < SpeedQueue_Size; roop++) {
            num += SpeedQueue[roop];
        }
        speed = num / (double) (SpeedQueue_Size);
        return speed;
    }

    private int NextQueue(int QueueNum) {
        QueueNum++;
        if (QueueNum == SpeedQueue_Size) {
            QueueNum = 0;
        }
        return QueueNum;
    }

    private void setStatus(BleStatus status) {
        mStatus = status;
        mHandler.sendMessage(status.message());
    }

    private enum BleStatus {
        DISCONNECTED, SCANNING, SCAN_FAILED, DEVICE_FOUND, SERVICE_NOT_FOUND, SERVICE_FOUND,
        CHARACTERISTIC_NOT_FOUND, NOTIFICATION_REGISTERED, NOTIFICATION_REGISTER_FAILED, RESET;

        public Message message() {
            Message message = new Message();
            message.obj = this;
            return message;
        }
    }


    /**** Arduinoとの通信 ****/
    private static final int REQUEST_SELECT_DEVICE = 1;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;

    /**
     * 接続
     **/
    private void Aruduino_connect() {
        if (!mBtAdapter.isEnabled()) {
        } else {
            Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
            startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
        }
    }

    /**
     * UARTサービス接続
     **/
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            if (!mService.initialize()) {
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /**送信可能状態**/
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                    }
                });
            }

            /**送信不可能状態**/
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        /**再接続**/
                        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
                        service_init();
                        Aruduino_connect();
                    }
                });
            }
        }
    };

    /**
     * サービス初期設定
     **/
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    /**
     * Gatt通信設定
     **/
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == RESULT_SETINGACTIVITY) {
                LimitSpeed = data.getDoubleExtra("speed", LimitSpeed);
                tirePerimeter = data.getIntExtra("tire", tirePerimeter);
                TirePos = data.getIntExtra("tirepos", TirePos);
                SpeedPos = data.getIntExtra("speedpos", SpeedPos);
            } else {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                mService.connect(deviceAddress);
            }
        }
    }


    /**** 画像処理部分 ****/
    private CameraBridgeViewBase mCameraView;
    private SensorManager manager;
    int Size = 20000;
    long currentTimeMillis = System.currentTimeMillis();
    int count = 0;
    public double value_x, value_y;
    public String str;
    String filePath = Environment.getExternalStorageDirectory() + "/Pictures2" + "/" + currentTimeMillis + ".txt";
    File file = new File(filePath);

    private File car_cascadeFile;
    private File human_cascadeFile;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    try {
                        InputStream car_is = getResources().openRawResource(R.raw.cars);
                        InputStream human_is = getResources().openRawResource(R.raw.haarcascade_fullbody);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        car_cascadeFile = new File(cascadeDir, "cars.xml");
                        human_cascadeFile = new File(cascadeDir, "haarcascade_fullbody.xml");
                        FileOutputStream car_os = new FileOutputStream(car_cascadeFile);
                        FileOutputStream human_os = new FileOutputStream(human_cascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = car_is.read(buffer)) != -1) {
                            car_os.write(buffer, 0, bytesRead);
                        }

                        while ((bytesRead = human_is.read(buffer)) != -1) {
                            human_os.write(buffer, 0, bytesRead);
                        }
                        car_is.close();
                        car_os.close();
                        human_is.close();
                        human_os.close();

                        cascadeDir.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e("a", "Failed to load cascade. Exception thrown: " + e);
                    }


                    mCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    ImageView image1;
    ImageView image2;
    boolean danger_flag = false;
    boolean speed_flag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** スピードセンサ接続**/
        speedText = (TextView) findViewById(R.id.speedView);
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (!mBluetoothAdapter.isEnabled()) {//bluetooth接続確認
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mStatusText = (TextView) findViewById(R.id.text_status);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mStatusText.setText(((BleStatus) msg.obj).name());
            }
        };

        reset();


        /** Arduino接続 **/
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        service_init();
        Aruduino_connect();


        /** 画像処理**/
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        mCameraView.setMaxFrameSize(320, 240);
        mCameraView.setCvCameraViewListener(this);
        //mCameraView.disableFpsMeter();

        image2 = (ImageView) findViewById(R.id.warning2);
        image2.setImageResource(R.drawable.warning2);

        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                danger_flag = !danger_flag;

                if (danger_flag) {
                    image2.setVisibility(View.VISIBLE);
                } else {
                    image2.setVisibility(View.GONE);
                }
            }
        });


        image1 = (ImageView) findViewById(R.id.warning1);
        image1.setImageResource(R.drawable.warning1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                speed_flag = !speed_flag;

                if (speed_flag) {
                    image1.setVisibility(View.VISIBLE);
                } else {
                    image1.setVisibility(View.GONE);
                }
            }
        });



        /**
         * 設定画面ボタン
         **/
        Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), ic_menu_manage);
        ImageButton SettingButton = (ImageButton) findViewById(R.id.SettingButton);
        SettingButton.setImageBitmap(bmp1);
        SettingButton.setOnClickListener(new View.OnClickListener() {

            // クリック時に呼ばれるメソッド
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                intent.putExtra("speedpos", SpeedPos);
                intent.putExtra("tirepos", TirePos);
                startActivityForResult(intent, RESULT_SETINGACTIVITY);
            }
        });

        if (!OpenCVLoader.initDebug()) {
            Log.i("OpenCV", "Failed");
        } else {
            Log.i("OpenCV", "successfully built !");
        }

    }

    @Override
    public void onPause() {
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        manager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor Magnet = manager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        manager.registerListener(this, Magnet, SensorManager.SENSOR_DELAY_UI);

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCameraView != null) {
            mCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat inputFrame) {
        byte[] value2;
        try {

            final Calendar calendar = Calendar.getInstance();
            final int hour = calendar.get(Calendar.HOUR_OF_DAY);
            final int minute = calendar.get(Calendar.MINUTE);
            final int second = calendar.get(Calendar.SECOND);
            final int ms = calendar.get(Calendar.MILLISECOND);
            if (OpticalFlow(car_cascadeFile.getAbsolutePath(), human_cascadeFile.getAbsolutePath(), inputFrame.getNativeObjAddr(), value_x)) {
//                image2.setVisibility(View.VISIBLE);
                String message = "B";
                value2 = message.getBytes("UTF-8");
                mService.writeRXCharacteristic(value2);//警告送信
            }
            if (danger_flag) {
                try {
                    String message = "Z";
                    value2 = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value2);//警告送信
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (speed_flag) {
                try {
                    String message = "B";
                    value2 = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value2);//警告送信
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

//            //DPI を取得する
//            str = count + ":\n";
//            str += hour + "" + minute + "" + second + "" + ms + "\n";
//            str += speed1 + "\n" + value_x + "\n" + value_y + "\n";
//            saveFile(str);
//            if (count < Size) {
//                String imgFilePath = Environment.getExternalStorageDirectory() + "/Pictures2" + "/" + currentTimeMillis + "-" + count + ".png";
//                Imgproc.cvtColor(inputFrame, inputFrame, Imgproc.COLOR_BGRA2RGB);
//                Imgcodecs.imwrite(imgFilePath, inputFrame);
//                count++;
//            }
//            if (count == Size) {
//                this.finish();
//                this.moveTaskToBack(true);
//            }
            Log.i("OpenCV", "seikou");


            return inputFrame;
        } catch (Exception ex) {
            ex.printStackTrace();
            this.finish();
            this.moveTaskToBack(true);
            return inputFrame;
        }
    }

    public native boolean OpticalFlow(String car_cascadeName, String human_cascadeName, long src, double _x);

    static {
        System.loadLibrary("native-lib");
    }

    public void saveFile(String _str) {
        if (!file.isFile()) {
            file.getParentFile().mkdir();
            Log.i("OpenCV", "seikou");
        } else {
            Log.i("OpenCV", "sd");
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter osw = new OutputStreamWriter(fileOutputStream, "UTF-8");
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(_str);
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            value_x = event.values[SensorManager.DATA_X];
            value_y = event.values[SensorManager.DATA_Y];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
