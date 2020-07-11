package io.idhea.tcnserial;

import android.app.Activity;
import android.serialport.SerialPort;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.Thread;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

/** TcnserialPlugin */
public class TcnserialPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, RequestPermissionsResultListener, EventChannel.StreamHandler {
  private static final String TAG = "TCNSerial";
  private MethodChannel channel;
  private EventChannel eventChannel;

  private EventChannel.EventSink mEventSink;
  protected OutputStream mOutputStream;
  private InputStream mInputStream;
  private ReadThread mReadThread;
  private Handler mHandler = new Handler(Looper.getMainLooper());
  protected SerialPort mSerialPort;
  private FlutterPluginBinding pluginBinding;
  private static TcnserialPlugin instance;
  private static final String NAMESPACE = "io.idhea.tcnserial/tcnserial";
  private ActivityPluginBinding activityBinding;
  private Activity activity;

  public TcnserialPlugin() {
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    pluginBinding = flutterPluginBinding;
  }

  public static void registerWith(Registrar registrar) {
    if (instance == null) {
      instance = new TcnserialPlugin();
    }
    Activity activity = registrar.activity();
    instance.setup(registrar.messenger(), registrar, activity);
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activityBinding = binding;
    setup(pluginBinding.getBinaryMessenger(), null, activityBinding.getActivity());
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    tearDown();
  }


  private void setup(final BinaryMessenger messenger, final PluginRegistry.Registrar registrar, final Activity activity) {
      this.activity = activity;
      channel = new MethodChannel(messenger, NAMESPACE + "/methods");
      channel.setMethodCallHandler(this);
      eventChannel = new EventChannel(messenger, NAMESPACE + "/event");
      eventChannel.setStreamHandler(this);
      if (registrar != null) {
        // V1 embedding setup for activity listeners.
        registrar.addRequestPermissionsResultListener(this);
      } else {
        // V2 embedding setup for activity listeners.
        activityBinding.addRequestPermissionsResultListener(this);
      }
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {

    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "open":
        final String devicePath = call.argument("devicePath");
        final int baudrate = call.argument("baudrate");
        Boolean openResult = openDevice(devicePath, baudrate);
        result.success(openResult);
        break;
      case "close":
        Boolean closeResult = closeDevice();
        result.success(closeResult);
        break;
      case "tcnCommand":
        try {
          JSONObject obj = new JSONObject((String) call.arguments());
          processTCNCommand(obj);
          result.success(true);
        } catch (JSONException e) {
          e.printStackTrace();
        }
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  private void getElevatorStatus() {
    byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
    try {
      mOutputStream.write(bytesToSend, 0, 7);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void processTCNCommand(JSONObject obj) {
    try {
      byte[] bytesToSend;
      String command = (String) obj.get("command");
      switch (command) {
        case "statusElevator":
          getElevatorStatus();
          break;
        case "shipment":
          int slot = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(slot),0x00,0x00,(byte) (byte)(slot),0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 10);
          break;
        case "clearElevatorFault":
          bytesToSend = new byte[]{0x02, 0x03, 0x50, 0x00, 0x00, 0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 7);
          break;
        case "backElevatorToOrigin":
          bytesToSend = new byte[]{0x02, 0x03, 0x05,0x00, 0x00, 0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 7);
          break;
      }
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    } catch (JSONException e) {
      Log.e(TAG, e.toString());
    }
  }

  private Boolean openDevice(String devicePath, int baudrate) {
    if (mSerialPort == null) {
      /* Check parameters */
      if ((devicePath.length() == 0) || (baudrate == -1)) {
        return false;
      }

      /* Open the serial port */
      try {
        mSerialPort = new SerialPort(new File(devicePath), baudrate, 0);
        mOutputStream = mSerialPort.getOutputStream();
        mInputStream = mSerialPort.getInputStream();
        mReadThread = new ReadThread();
        mReadThread.start();
        return true;
      } catch (Exception e) {
        Log.e(TAG, e.toString());
        return false;
      }
    }
    return false;
  }

  private Boolean closeDevice() {
    if (mSerialPort != null) {
      mSerialPort.close();
      mSerialPort = null;
      return true;
    }
    return false;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    pluginBinding = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    return false;
  }

  private class ReadThread extends Thread {
    @Override
    public void run() {
      super.run();
      while (!isInterrupted()) {
        int size;
        try {
          byte[] buffer = new byte[64];
          if (mInputStream == null)
            return;
          size = mInputStream.read(buffer);
          if (size > 0) {
            onDataReceived(buffer, size);
          }
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
      }
    }
  }

  protected void onDataReceived(final byte[] buffer, final int size) throws UnsupportedEncodingException {
    invokeMethodUIThread("dataSerial", bytesToHex(Arrays.copyOfRange(buffer, 0, size)));
  }

  private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for ( int j = 0; j < bytes.length; j++ ) {
          int v = bytes[j] & 0xFF;
          hexChars[j * 2] = hexArray[v >>> 4];
          hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
  }

  private void invokeMethodUIThread(final String name, final String result) {
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        channel.invokeMethod(name, result);
      }
    });
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    mEventSink = eventSink;
  }

  @Override
  public void onCancel(Object o) {
    mEventSink = null;
  }

  private void tearDown() {
    Log.i(TAG, "teardown");
    activityBinding.removeRequestPermissionsResultListener(this);
    activityBinding = null;
    channel.setMethodCallHandler(null);
    channel = null;
    eventChannel.setStreamHandler(null);
  }

}
