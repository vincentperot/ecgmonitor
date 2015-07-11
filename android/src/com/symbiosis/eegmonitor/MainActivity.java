package com.symbiosis.eegmonitor;

import java.util.Arrays;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader.TileMode;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.Plot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    

    private XYPlot dynamicPlot;
    private SimpleXYSeries eeg1;
    final private static int HISTORY_SIZE = 512;
    private double mostRecentTime;
    
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;
    
    private static final int SAMPLE_PER_PACKET = 3;
    private static final int SAMPLE_SIZE = 6;
    private static final float SAMPLE_CORRECT = 2;
    private static final float TOTAL_GAIN = (float) (SAMPLE_CORRECT*14.1*71.2*4096/3000);
    private int state;

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;

    private Button enableBluetoothButton;
    private TextView scanStatusText;
    private Button scanButton;
    private TextView deviceInfoText;
    private TextView connectionStatusText;
    private Button connectButton;
    private Button startSamplingButton;
    private Button stopSamplingButton;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            updateUi();
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.symbiosis.eegmonitor.R.layout.layout_main);
        
        mostRecentTime = 0;
        
        // Plot   
        LineAndPointFormatter formatter = new LineAndPointFormatter(Color.BLACK, null, Color.rgb(0, 145, 255), (PointLabelFormatter) null);
        Paint lineFill = new Paint();
        lineFill.setAlpha(175);
        lineFill.setShader(new LinearGradient(0, 0, 0, 624, Color.rgb(0, 145, 255), Color.WHITE, TileMode.CLAMP));

        formatter.setFillPaint(lineFill);

        dynamicPlot = (XYPlot) findViewById(com.symbiosis.eegmonitor.R.id.dynamicPlot);
        eeg1 = new SimpleXYSeries("ECG");
        dynamicPlot.setRangeBoundaries(-2048/TOTAL_GAIN, 2048/TOTAL_GAIN, BoundaryMode.FIXED);
        dynamicPlot.setDomainBoundaries(0, 1, BoundaryMode.AUTO);
        dynamicPlot.addSeries(eeg1, formatter);
        dynamicPlot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 0.5);
        dynamicPlot.setRangeStep(XYStepMode.SUBDIVIDE, 5);
                
        dynamicPlot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
        dynamicPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.TRANSPARENT);
        dynamicPlot.getGraphWidget().getRangeSubGridLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainSubGridLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainGridLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getRangeGridLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getCursorLabelBackgroundPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getCursorLabelPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainOriginLabelPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getRangeOriginLabelPaint().setColor(Color.WHITE);
        dynamicPlot.setBorderStyle(Plot.BorderStyle.NONE, null, null);
        dynamicPlot.getLegendWidget().getTextPaint().setColor(Color.WHITE);
        
        dynamicPlot.setDomainLabel("Time (s)");
        dynamicPlot.getDomainLabelWidget().pack();
        dynamicPlot.setRangeLabel("Voltage (mV)");
        dynamicPlot.getRangeLabelWidget().pack();
        
        // Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        enableBluetoothButton = (Button) findViewById(com.symbiosis.eegmonitor.R.id.enableBluetooth);
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });

        // Find Device
        scanStatusText = (TextView) findViewById(com.symbiosis.eegmonitor.R.id.scanStatus);

        scanButton = (Button) findViewById(com.symbiosis.eegmonitor.R.id.scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStarted = true;
                bluetoothAdapter.startLeScan(
                        new UUID[]{ RFduinoService.UUID_SERVICE },
                        MainActivity.this);
            }
        });

        // Device Info
        deviceInfoText = (TextView) findViewById(com.symbiosis.eegmonitor.R.id.deviceInfo);

        // Connect Device
        connectionStatusText = (TextView) findViewById(com.symbiosis.eegmonitor.R.id.connectionStatus);

        connectButton = (Button) findViewById(com.symbiosis.eegmonitor.R.id.connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                connectionStatusText.setText("Connecting...");
                Intent rfduinoIntent = new Intent(com.symbiosis.eegmonitor.MainActivity.this, com.symbiosis.eegmonitor.RFduinoService.class);
                bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
            }
        });

        startSamplingButton = (Button) findViewById(com.symbiosis.eegmonitor.R.id.start);
        startSamplingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                while(eeg1.size() != 0)
                {
                	eeg1.removeFirst();
                }
                dynamicPlot.redraw();
                mostRecentTime = 0;
                rfduinoService.send(new byte[]{1});
            }
        });
        
        stopSamplingButton = (Button) findViewById(com.symbiosis.eegmonitor.R.id.stop);
        stopSamplingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rfduinoService.send(new byte[]{0});
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;
        enableBluetoothButton.setEnabled(!on);
        enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        // Scan
        if (scanStarted && scanning) {
            scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }
        connectionStatusText.setText(connectionText);
        connectButton.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);

        // Send
        startSamplingButton.setEnabled(connected);
	stopSamplingButton.setEnabled(connected);
    }

    private void addData(byte[] data) {
    	
    	for(int i = 0; i < SAMPLE_PER_PACKET*SAMPLE_SIZE; i += SAMPLE_SIZE)
    	{
	    	// Retrieve data
	    	long sampleTime = HexAsciiHelper.byteArrayToInt(Arrays.copyOfRange(data, i, i+4));
	    	
	    	int sampleValue = HexAsciiHelper.byteArrayToSample(Arrays.copyOfRange(data, i+4, i+6));
	    	double realSampleTime = sampleTime;
	    	float realSampleValue = (float) SAMPLE_CORRECT*(sampleValue - 2048)/TOTAL_GAIN;
	    	if(realSampleValue > 2047/TOTAL_GAIN) {realSampleValue = 2047/TOTAL_GAIN;} else if(realSampleValue < -2047/TOTAL_GAIN) {realSampleValue = -2047/TOTAL_GAIN;}
	    	System.out.println("Number of bytes " + data.length);
	    	System.out.println("Data Received: " + HexAsciiHelper.bytesToHex(data));
	    	System.out.println("Sample Time and Value: " + realSampleTime + ", " + realSampleValue);
	    	
	    	// Add to plotData
	    	if(realSampleTime > mostRecentTime ) // Packet is valid
	    	{
	    		mostRecentTime = realSampleTime;
		        // get rid the oldest sample in history:
		        if (eeg1.size() > HISTORY_SIZE) {
		            eeg1.removeFirst();
		        }
		        // add the latest history sample:
		        eeg1.addLast(realSampleTime/1000.0, realSampleValue);
	    	}
	        
    	}
    	
        // UpdateUI
    	dynamicPlot.redraw();

   }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                updateUi();
            }
        });
    }

}

