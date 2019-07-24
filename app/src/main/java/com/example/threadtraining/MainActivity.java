package com.example.threadtraining;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    //xml init
    private TextView detect;
    private GraphView graph;
    private Button record;
    private Button stop_record;
    private Button play;
    private Button stop;
    private Button graph_init;
    private Button graph_draw;

    //Record settings
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    //file error statement
    final int REQUEST_PERMISSION_CODE=1000;
    File extStore1 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    String extStore = Environment.getExternalStorageDirectory().getPath();

    final String pathsave= extStore1.getAbsolutePath()+"/"+ UUID.randomUUID()+toString()+"Audio_recorder.txt";
    final File fiche = new File(pathsave);

    //fft part
    byte [] music;
    short[] music2Short;
    InputStream is;
    AudioTrack audioOut=null;
    int deltaF=RECORDER_SAMPLERATE/1024;
    boolean graphDraw=false;
    private Thread calculThread;
    int boucle=0;

    int minSize = AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    //play/stop part
    MediaPlayer mediaPlayer;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(!checkPermissionFromDevice()){
            RequestPermission();
        }

        record=findViewById(R.id.record);
        stop_record=findViewById(R.id.stopRecord);
        stop=findViewById(R.id.stop);
        play=findViewById(R.id.play);
        graph_draw=findViewById(R.id.calcul);
        graph_init=findViewById(R.id.initialise);
        detect = findViewById(R.id.detection);
        graph= findViewById(R.id.graph);

        enableButtons(false);

        bufferSize =AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);


        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AppLog.logString("Start Recording");
                enableButtons(true);
                startRecording();

                //playAudio();
                startCalculating();
            }
        });


        stop_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AppLog.logString("Start Recording");

                // PrintFile("Stop",fiche);

                enableButtons(false);
                try {
                    stopRecording();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //stopCalculating();
            }
        });

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
                Toast.makeText(MainActivity.this, "Playing", Toast.LENGTH_SHORT).show();

            }
        });


        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mediaPlayer!=null){
                    mediaPlayer.stop();
                    mediaPlayer.release();
                }
            }
        });

        graph_draw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        graph_init.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                graph.removeAllSeries();
            }
        });


    }


    private void enableButton(int id,boolean isEnable){
        ((Button)findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.record,!isRecording);
        enableButton(R.id.stopRecord,isRecording);
    }

    private String getFilename() throws IOException {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.createNewFile();
        }

        return (extStore + "/" + "AudioRead" + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename() throws IOException {
        //String filepath = Environment.getExternalStorageDirectory().getPath();
        String filepath =Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath);

        if(!file.exists()){
            file.createNewFile();
        }

        File tempFile = new File(filepath);

        if(tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void startCalculating(){
        calculThread= new Thread(new Runnable() {
            @Override
            public void run() {

                playAudio();
                int boucle=0;
                if (is != null) {
                  while (isRecording) {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                          e.printStackTrace();
                      }
                      //playAudio();
                      int i;
                      FFT a = new FFT(1024, RECORDER_SAMPLERATE);
                      //buffer with the signal
                      try {
                          while (((i = is.read(music)) != -1)) {
                              ByteBuffer.wrap(music).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(music2Short);
                          }
                      } catch (IOException e) {
                          e.printStackTrace();
                      }

                      a.forward(Tofloat(music2Short));

                      BarGraphSeries<DataPoint> series = new BarGraphSeries<>(generateData(a, 800));
                      series.resetData(generateData(a,800));

                      graph.addSeries(series);
                      int frequency = 860;

                      boucle++;

                      int x = frequency / 43;
                      int j = Math.round(x);
                      float seuil = Math.abs(a.real[j] * a.real[j] + a.imag[j] * a.imag[j]) / 100000;
                      int aff = Math.round(seuil);
                      if (aff >= 1000) {
                          detect.setText("boucle n°:" + boucle + "detected : " + aff + "\n");
                          //play();
                      } else {
                          detect.setText("boucle n°:" + boucle + "not detected : " + aff + "\n");
                      }
                  }
                }

            }
        });

        calculThread.start();
    }

    private void startRecording(){
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);

        int i = recorder.getState();
        if(i==1)
            recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    writeAudioDataToFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        },"AudioRecorder Thread");

        recordingThread.start();
    }

    private void writeAudioDataToFile() throws IOException {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            PrintFile("erreur :" + e, fiche);
// TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if(null != os){
            while(isRecording){
                read = recorder.read(data, 0, bufferSize);

                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopCalculating(){
        if(null != calculThread){
            calculThread.stop();
            calculThread = null;
        }
    }

    private void stopRecording() throws IOException {
        if(null != recorder){
            isRecording = false;

            int i = recorder.getState();
            if(i==1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(),getFilename());
        deleteTempFile();
    }

    private void deleteTempFile() throws IOException {
        File file = new File(getTempFilename());

        file.delete();
    }

    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }


    private void PrintFile(String text, File fiche){

        try {
            final FileWriter writer = new FileWriter(fiche);
            try {
                writer.write(text +"\n");
            } finally {
                // quoiqu'il arrive, on ferme le fichier
                writer.close();
            }
        } catch (Exception e) {
            System.out.println("Impossible de creer le FileWriter");
        }
    }

    private boolean checkPermissionFromDevice() {
        int result_from_storage_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int record_audio_result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return (result_from_storage_permission == PackageManager.PERMISSION_GRANTED) &&
                (record_audio_result == PackageManager.PERMISSION_GRANTED);
    }

    private void RequestPermission(){
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQUEST_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode)
        {
            case REQUEST_PERMISSION_CODE:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(MainActivity.this, "Permission granted", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
            }
            break;
        }

    }

    public void initialize(){

        File initialFile = null;
        try {
            initialFile = new File(getFilename());
        } catch (IOException e) {
            e.printStackTrace();
            detect.setText("init fft:" +e);
        }
        try{
            is = new FileInputStream(initialFile);
        }
        catch (IOException e){
            e.printStackTrace();
        }


        audioOut = new AudioTrack(
                AudioManager.STREAM_MUSIC,          // Stream Type
                RECORDER_SAMPLERATE,                         // Initial Sample Rate in Hz
                AudioFormat.CHANNEL_OUT_MONO,       // Channel Configuration
                AudioFormat.ENCODING_PCM_16BIT,     // Audio Format
                minSize,                            // Buffer Size in Bytes
                AudioTrack.MODE_STREAM);            // Streaming static Buffer

    }

    public void playAudio() {

        this.initialize();

        if ( (minSize/2) % 2 != 0 ) {
            /*If minSize divided by 2 is odd, then subtract 1 and make it even*/
            music2Short     = new short [((minSize /2) - 1)/2];
            music           = new byte  [(minSize/2) - 1];
        }
        else {
            /* Else it is even already */
            music2Short     = new short [minSize/2]; //pour motorola sinon /4
            music           = new byte  [minSize];  //pour motorola sinon /2
        }

    }

    public float[] Tofloat(short[] s){
        int len = s.length;
        float[] f= new float[len];
        for (int i=0;i<len;i++){
            f[i]=s[i];
        }
        return f;
    }

    private DataPoint[] generateData(FFT a, int total) {
        int x;
        double y;
        float c;
        float b;
        //float z;

        DataPoint[] values = new DataPoint[total];
        for (int j=0;j<total;j++){
            x=j*deltaF;
            c=a.real[j]*a.real[j];
            b=a.imag[j]*a.imag[j];
            y=Math.abs(b+c)/1000000000;
            DataPoint v = new DataPoint(x,y);
            values[j]=v;
        }
        return values;
    }

    private DataPoint[] generateSelectedDataSpike(FFT a, int[] values) {
        double y;
        int x;
        float c;

        int len=values.length;
        DataPoint[] point = new DataPoint[len];
        for (int j=0;j<len;j++){

            x=values[j];
            //frequency conversion to point
            float b= x/43;
            int d = Math.round(b);
            c=a.real[d]*a.real[d];
            b=a.imag[d]*a.imag[d];
            y=Math.abs(b+c)/1000000000;
            DataPoint v = new DataPoint(x,y);
            point[j]=v;
        }
        return point;
    }

    public boolean calculSeuil(int frequency, FFT a){
        float x= frequency/43;
        int j = Math.round(x);
        float seuil=Math.abs(a.real[j]*a.real[j]+a.imag[j]*a.imag[j]);
        int test= Math.round(seuil);

        return test<=100;
    }

    public void play(){
        if (mediaPlayer == null){
            mediaPlayer = new MediaPlayer();

            mediaPlayer=MediaPlayer.create(MainActivity.this, R.raw.son17);

            mediaPlayer.start();
        }
        else{
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=MediaPlayer.create(MainActivity.this, R.raw.son17);

            mediaPlayer.start();
        }

    }
}
