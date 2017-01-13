package com.prolific.pluartmultisimpletest;

import java.io.IOException;
import tw.com.prolific.pl2303multilib.PL2303MultiLib;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;


class UARTSettingInfo {
	public int iPortIndex = 0;
	public PL2303MultiLib.BaudRate mBaudrate = PL2303MultiLib.BaudRate.B115200;
	public PL2303MultiLib.DataBits mDataBits = PL2303MultiLib.DataBits.D8;
	public PL2303MultiLib.Parity mParity = PL2303MultiLib.Parity.NONE;
	public PL2303MultiLib.StopBits mStopBits = PL2303MultiLib.StopBits.S1;
	public PL2303MultiLib.FlowControl mFlowControl = PL2303MultiLib.FlowControl.OFF;		
}//class UARTSettingInfo

public class MainActivity extends Activity {

	private static boolean bDebugMesg = true;
	
	PL2303MultiLib mSerialMulti;

    private static enum DeviceOrderIndex {
    	DevOrder1, 
    	DevOrder2,
    	DevOrder3,
    };
    
    private static final int DeviceIndex1 = 0;
	
	private Button btOpen1;
    private TextView tvRead1;
    private Spinner spBaudRate1;

            
    private static final int MAX_DEVICE_COUNT = 4;
    private static final String ACTION_USB_PERMISSION = "com.prolific.pluartmultisimpletest.USB_PERMISSION";
    private UARTSettingInfo gUARTInfoList[];   
    private int iDeviceCount = 0;
    private boolean bDeviceOpened[] = new boolean[MAX_DEVICE_COUNT];
    
    private boolean gThreadStop[] = new boolean[MAX_DEVICE_COUNT];
    private boolean gRunningReadThread[] = new boolean[MAX_DEVICE_COUNT];
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		spBaudRate1 = (Spinner)findViewById(R.id.DevSpinner1);
		ArrayAdapter<CharSequence> adapter =
				ArrayAdapter.createFromResource(this, R.array.BaudRateList, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spBaudRate1.setAdapter(adapter);		
		spBaudRate1.setOnItemSelectedListener(new MyOnItemSelectedListener());
		spBaudRate1.setSelection(5);//baudrate = 9600, base is 0
		spBaudRate1.setEnabled(false);
		btOpen1 = (Button)findViewById(R.id.OpenButton1);
		btOpen1.setOnClickListener(new Button.OnClickListener() {		
			public void onClick(View v) {		
				OpenUARTDevice(DeviceIndex1);
			}
		});
		btOpen1.setEnabled(false);
		tvRead1 = (TextView)findViewById(R.id.tvText1);
		mSerialMulti = new PL2303MultiLib((UsbManager) getSystemService(Context.USB_SERVICE),
           	  	this, ACTION_USB_PERMISSION);
		gUARTInfoList = new UARTSettingInfo[MAX_DEVICE_COUNT];
		for(int i=0;i<MAX_DEVICE_COUNT;i++) {
			gUARTInfoList[i] = new UARTSettingInfo(); 	
			gUARTInfoList[i].iPortIndex = i;
		    gThreadStop[i] = false;
		    gRunningReadThread[i] = false;	
		    bDeviceOpened[i] = false;
		}
	}
	public void onPause() {
		super.onStart();
	}
	public void onRestart() {
    	super.onRestart();
	}
   	protected void onStop() {
    	super.onStop();
    }
    protected void onDestroy() {
    	if(mSerialMulti!=null) {
    		for(int i=0;i<MAX_DEVICE_COUNT;i++) {
    		    gThreadStop[i] = true;
    		}//First to stop app view-thread
    		if(iDeviceCount>0)
    			unregisterReceiver(PLMultiLibReceiver);
    		mSerialMulti.PL2303Release();
    		mSerialMulti = null;
    	}
    	super.onDestroy();        
    }
    public void onStart() {
    	super.onStart();
    }
    public void onResume() {
    	super.onResume();
    	String action =  getIntent().getAction();
   		iDeviceCount = mSerialMulti.PL2303Enumerate();
       	if( 0==iDeviceCount ) {
            SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder1,false,false);
            SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder2,false,false);
       		SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder3,false,false);
       		Toast.makeText(this, "no more devices found", Toast.LENGTH_SHORT).show();      
       	} else {   
       		if(!bDeviceOpened[DeviceIndex1]) {
       			SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder1, true, false);
       		}
       		IntentFilter filter = new IntentFilter();
       	    filter.addAction(mSerialMulti.PLUART_MESSAGE); 
       	    registerReceiver(PLMultiLibReceiver, filter);
   			Toast.makeText(this, "The "+iDeviceCount+" devices are attached", Toast.LENGTH_SHORT).show();
       	}//if( 0==iDevCnt )        	
    }//public void onResume()
    private final BroadcastReceiver PLMultiLibReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
           if(intent.getAction().equals(mSerialMulti.PLUART_MESSAGE)){
        	   Bundle extras = intent.getExtras();
        	   if(extras!=null) {
        		   String str = (String)extras.get(mSerialMulti.PLUART_DETACHED);
        		   int index = Integer.valueOf(str);
        		   if(DeviceIndex1==index) {
               		   SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder1,false,false);   
               		   spBaudRate1.setEnabled(false);
               		   bDeviceOpened[DeviceIndex1] = false;
        		   }
        	   }        	   
           }    
        }//onReceive
     };
    private void SetEnabledDevControlPanel(DeviceOrderIndex iDev, boolean bOpen, boolean bWrite) {
    	switch(iDev) {
    		case DevOrder1:
    	   		spBaudRate1.setEnabled(true);
    	   		btOpen1.setEnabled(bOpen); 
    			break;
    	}
    }
    private void OpenUARTDevice(int index) {
   	 	if(mSerialMulti==null)
   	 		return;
        if(!mSerialMulti.PL2303IsDeviceConnectedByIndex(index)) 
         	return;
		boolean res;
		UARTSettingInfo info = gUARTInfoList[index];
		res = mSerialMulti.PL2303OpenDevByUARTSetting(index, info.mBaudrate, info.mDataBits, info.mStopBits, 
					info.mParity, info.mFlowControl);
		if( !res ) {
			Toast.makeText(this, "Can't set UART correctly!", Toast.LENGTH_SHORT).show();
			return;
		}
		if(DeviceIndex1==index) {
			SetEnabledDevControlPanel(DeviceOrderIndex.DevOrder1, false, true);		
		}
		bDeviceOpened[index] = true;
		if(!gRunningReadThread[index]) {
			UpdateDisplayView(index);
		}
   	 	Toast.makeText(this, "Open ["+ mSerialMulti.PL2303getDevicePathByIndex(index) +"] successfully!", Toast.LENGTH_SHORT).show();
    	return;
    }//private void OpenUARTDevice(int index)
    private void UpdateDisplayView(int index) {
    	gThreadStop[index] = false;
	    gRunningReadThread[index] = true;	 
	    
    	if( DeviceIndex1==index ) {
    		new Thread(ReadLoop1).start();    		
    	}
    }
    private int ReadLen1;
    private byte[] ReadBuf1 = new byte[4096];    
    Handler mHandler1 = new Handler();
    private Runnable ReadLoop1 = new Runnable() {
        public void run() {
            for (;;) {
            	ReadLen1 = mSerialMulti.PL2303Read(DeviceIndex1, ReadBuf1);
                if (ReadLen1 > 0) {
                 	mHandler1.post(new Runnable() {
                 		public void run() {
                            if (ReadLen1 == 11) {
                                StringBuffer WeightData = new StringBuffer();
                                for (int j = 0; j < ReadLen1; j++) {
                                    //sbHex.append((char) (ReadBuf1[j] & 0x000000FF));
                                    WeightData.append((char) (ReadBuf1[j] & 0x000000FF));
                                }
                                String weight = WeightData.substring(1, 8);
                                tvRead1.setText(weight);
                            }
                 		}//run
                 	});//Handler.post
                }//if (len > 0)
                DelayTime(60);
                if (gThreadStop[DeviceIndex1]) {
                	gRunningReadThread[DeviceIndex1] = false;
                	return;
                }//if                
            }//for(...)
        }//run
    };//Runnable
    private void WriteToUARTDevice(int index) {
   	 	if(mSerialMulti==null)
   	 		return;
        if(!mSerialMulti.PL2303IsDeviceConnectedByIndex(index)) 
         	return;
        String strWrite = null;
        if( strWrite==null || "".equals(strWrite.trim()) ) { //str is empty
        	return;
        }
        int res = mSerialMulti.PL2303Write(index, strWrite.getBytes());
    	if( res<0 ) {
    		return;
    	}         	
    } //private void WriteToUARTDevice(int index)
 	public class MyOnItemSelectedListener implements OnItemSelectedListener {
  		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {  			
  			 Spinner spinner = (Spinner) parent;
  			 String newBaudRate = spinner.getItemAtPosition(position).toString();
  			 int iBaudRate=0, iSelected = 0; 
  			 UARTSettingInfo info = new UARTSettingInfo(); 
  			 PL2303MultiLib.BaudRate rate;
 			 if(mSerialMulti==null)
  				 return;
  		     if(R.id.DevSpinner1 == spinner.getId())  {
  		    	iSelected = DeviceIndex1;
  		     }
             if(!mSerialMulti.PL2303IsDeviceConnectedByIndex(iSelected)) 
             	return;
             info.iPortIndex = iSelected;
  			 try {
  				iBaudRate= Integer.parseInt(newBaudRate);
  			 }
  			 catch (NumberFormatException e)	{
  				System.out.println(" parse int error!!  " + e);
  			 }
			 switch (iBaudRate) {
      		 	case 75:
      		 		rate = PL2303MultiLib.BaudRate.B75;
      		 		break;        		 
      		 	case 300:
      		 		rate = PL2303MultiLib.BaudRate.B300;
      		 		break;        		 
      		 	case 1200:
      		 		rate = PL2303MultiLib.BaudRate.B1200;
      		 		break;        		 
      		 	case 2400:
      		 		rate = PL2303MultiLib.BaudRate.B2400;
      		 		break;    
      		 	case 4800:
      		 		rate = PL2303MultiLib.BaudRate.B4800;
      		 		break;  
      		 	case 9600:
      		 		rate = PL2303MultiLib.BaudRate.B9600;
      		 		break;        	
      		 	case 14400:
      		 		rate = PL2303MultiLib.BaudRate.B14400;
      		 		break;
      		 	case 19200:
      		 		rate = PL2303MultiLib.BaudRate.B19200;
      		 		break;        		 
      		 	case 57600:
      		 		rate = PL2303MultiLib.BaudRate.B57600;
      		 		break;        		 
      		 	case 115200:
      		 		rate = PL2303MultiLib.BaudRate.B115200;      		 		
      		 		break;        		 
      		 	case 614400:
      		 		rate = PL2303MultiLib.BaudRate.B614400;      		 		
      		 		break;     
      		 	case 921600:
      		 		rate = PL2303MultiLib.BaudRate.B921600;
      		 		break;
      		 	case 1228800:
      		 		rate = PL2303MultiLib.BaudRate.B1228800;
      		 		break;
      		 	case 3000000:
      		 		rate = PL2303MultiLib.BaudRate.B3000000;
      		 		break;
      		 	case 6000000:
      		 		rate = PL2303MultiLib.BaudRate.B6000000;
      		 		break;
      		 	default:
      		 		rate = PL2303MultiLib.BaudRate.B9600;
      		 		break;        		  
      		 }   			   			   			 
             info.mBaudrate = rate;
 			int res = 0;
 			try {
 				res = mSerialMulti.PL2303SetupCOMPort(iSelected, info.mBaudrate, info.mDataBits, info.mStopBits,
 						info.mParity, info.mFlowControl);
				gUARTInfoList[iSelected] = info;	
 			} catch (IOException e) {
 				e.printStackTrace();
 			}
 			if( res<0 ) {
 				return;
 			}              			
  		}//public void onItemSelected
 		public void onNothingSelected(AdapterView<?> parent) {
  		}
 	}
    private void DelayTime(int dwTimeMS) {
		//Thread.yield();
		long StartTime, CheckTime;
		if(0==dwTimeMS) {
			Thread.yield();
			return;		
		}
		//Returns milliseconds running in the current thread
		StartTime = System.currentTimeMillis();
		do {
				CheckTime=System.currentTimeMillis();
				Thread.yield();
		 } while( (CheckTime-StartTime)<=dwTimeMS);		
	}
}