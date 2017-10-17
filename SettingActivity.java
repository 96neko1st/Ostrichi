package com.example.owner.myapplication;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;

import static com.example.owner.myapplication.R.mipmap.ic_launcher;

public class SettingActivity extends Activity {
    int SpeedPos;
    int TirePos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        Intent intent = getIntent();
        SpeedPos = intent.getIntExtra("speedpos", 0);
        TirePos = intent.getIntExtra("tirepos", 0);
        ((Spinner) findViewById(R.id.TireSize)).setSelection(TirePos);
        ((Spinner) findViewById(R.id.LimitSpeed)).setSelection(SpeedPos);
        /**
         * 設定画面終了ボタン
         **/
        Button EndButton = (Button) findViewById(R.id.SettingEnd);
        EndButton.setOnClickListener(new View.OnClickListener() {

            // クリック時に呼ばれるメソッド
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                Spinner TireSpinner = (Spinner) findViewById(R.id.TireSize);
                Spinner SpeedSpinner = (Spinner) findViewById(R.id.LimitSpeed);
                TirePos = TireSpinner.getSelectedItemPosition();
                SpeedPos = SpeedSpinner.getSelectedItemPosition();
                String[] TireSize_Value = getResources().getStringArray(R.array.TireSize_Values);
                String[] LimitSpeed_Value = getResources().getStringArray(R.array.LimitSpeed_Values);
                int TireSize = Integer.valueOf(TireSize_Value[TirePos]);//L(mm)
                double LimitSpeed = Double.valueOf(LimitSpeed_Value[SpeedPos]);//speed(km/h)
                intent.putExtra("tire", TireSize);
                intent.putExtra("speed", LimitSpeed);
                intent.putExtra("tirepos", TirePos);
                intent.putExtra("speedpos", SpeedPos);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}
