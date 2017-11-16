package compufacil.nfce.btprinter;

import com.RT_Printer.BluetoothPrinter.BLUETOOTH.BluetoothPrintDriver;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Euclécio Josias Rodrigues <eucjosias@gmail.com>
 */
public class MainActivity extends AppCompatActivity {

    // GUI Components
    private TextView deviceStatus;
    private Button mDiscoverBtn;
    private BluetoothAdapter mBTAdapter;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private ListView mockListView;
    private String deviceNameStr;
    private Dialog devicesDialog;

    private final String TAG = MainActivity.class.getSimpleName();
    
    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_TOAST = 5;


    // Member object for the chat services
    private BluetoothPrintDriver mChatService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.compufacil);

        deviceStatus = (TextView)findViewById(R.id.deviceStatus);
        mDiscoverBtn = (Button)findViewById(R.id.discover);

        ArrayAdapter<String> mockArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        mockListView = (ListView)findViewById(R.id.mockListView);
        mockListView.setAdapter(mockArrayAdapter); // assign model to view
        mockListView.setOnItemClickListener(mockListClickListener);

        mockArrayAdapter.add("NFC-e nº 55448\n10/09/2017");
        mockArrayAdapter.add("NFC-e nº 55447\n01/09/2017");
        mockArrayAdapter.add("NFC-e nº 55446\n28/08/2017");
        mockArrayAdapter.add("NFC-e nº 55445\n20/08/2017");
        mockArrayAdapter.add("NFC-e nº 55444\n12/08/2017");
        mockArrayAdapter.notifyDataSetChanged();

        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        devicesDialog = new Dialog(this);
        devicesDialog.setContentView(R.layout.devices_list);
        devicesDialog.setCancelable(true);
        devicesDialog.setTitle("Lista de dispositivos");

        mDevicesListView = (ListView)devicesDialog.findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        if (mBTAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(),"Dispositivo Bluetooth não encontrado!",Toast.LENGTH_SHORT).show();
        }
        else {
            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discover(v);
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBTAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothPrintDriver.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothPrintDriver(this, mBTHandler);
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mBTHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothPrintDriver.STATE_CONNECTED:
                            deviceStatus.setText("Conectado a " + deviceNameStr);
                            break;
                        case BluetoothPrintDriver.STATE_CONNECTING:
                            deviceStatus.setText("Conectando...");
                            break;
                        case BluetoothPrintDriver.STATE_LISTEN:
                        case BluetoothPrintDriver.STATE_NONE:
                            deviceStatus.setText("Impressora não conectada");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    String ErrorMsg = null;
                    byte[] readBuf = (byte[]) msg.obj;
                    float Voltage = 0;
                    if(readBuf[2]==0)
                        ErrorMsg = "SEM ERROR!         ";
                    else
                    {
                        if((readBuf[2] & 0x02) != 0)
                            ErrorMsg = "ERROR: Nenhuma impressora conectada!";
                        if((readBuf[2] & 0x04) != 0)
                            ErrorMsg = "ERROR: Sem papel!  ";
                        if((readBuf[2] & 0x08) != 0)
                            ErrorMsg = "ERROR: A tensão é muito baixa!  ";
                        if((readBuf[2] & 0x40) != 0)
                            ErrorMsg = "ERROR: Impressora sobre o calor!  ";
                    }
                    Voltage = (float) ((readBuf[0]*256 + readBuf[1])/10.0);
                    //if(D) Log.i(TAG, "Voltage: "+Voltage);
                    DisplayToast(ErrorMsg+"                                        "+"Voltagem da bateria£º"+Voltage+" V");
                    break;
                case MESSAGE_TOAST:
                    break;
            }
        }
    };

    public void DisplayToast(String str)
    {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 100);
        toast.show();
    }//DisplayToast

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            }
        }
    }

    private void discover(View view){
        // Check if the device is already discovering
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                devicesDialog.show();
            }
            else{
                Toast.makeText(getApplicationContext(), "O Bluetooth não está ligado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void printNFCe(View view){
        if(BluetoothPrintDriver.IsNoConnection()) {
            Toast.makeText(getApplicationContext(), "Nenhum dispositivo está conectado", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothPrintDriver.Begin();

        BluetoothPrintDriver.SetAlignMode((byte)1); // Center Align
        BluetoothPrintDriver.BT_Write("CNPJ: 00.445.335/0001-13 COMPUFACIL");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.BT_Write("RUA THEODORETO SOUT, 0, CENTRO, MANAUS, AM\n");
        BluetoothPrintDriver.BT_Write("Documento Auxiliar da Nota Fiscal de Consumidor Eletronica\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.SetAlignMode((byte)0); // Left Align
        BluetoothPrintDriver.BT_Write("Codigo Descricao  Qtde UN Vl Unit Vl Total");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.BT_Write("00010  Agua Miner 2,00 UN   2,500     5,00");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.BT_Write("00121  Agua C/Gas 3,00 UN   2,500     5,00");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.BT_Write("Qtde. total de itens                     5\n");
        BluetoothPrintDriver.BT_Write("Valor total R$                       10,00\n");
        BluetoothPrintDriver.BT_Write("Desconto R$                           1,00\n");
        BluetoothPrintDriver.BT_Write("Valor a Pagar R$                      9,00\n");
        BluetoothPrintDriver.BT_Write("FORMA DE PAGAMENTO           VALOR PAGO R$\n");
        BluetoothPrintDriver.BT_Write("Dinheiro                             10,00\n");
        BluetoothPrintDriver.BT_Write("Troco R$                              1,00\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.SetAlignMode((byte)1); // Center Align
        BluetoothPrintDriver.BT_Write("Consulte pela Chave de Acesso em\n" +
                "http://sistemas.sefaz.am.gov.br/nfceweb/formConsulta.do\n" +
                "1317 0600 4453 3500 0113 6509 9000 1456 6417 2781 9729\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.BT_Write("CONSUMIDOR\n");
        BluetoothPrintDriver.BT_Write("NOME: Filipe Vieira\n");
        BluetoothPrintDriver.BT_Write("CPF: 054.215.078-97\n");
        BluetoothPrintDriver.BT_Write("RUA PRINCIPAL, 123, CENTRO, CONCORDIA - SC\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.BT_Write("NFC-e N 0554 Serie 001 14/09/2017 08:40:51\n");
        BluetoothPrintDriver.BT_Write("Protocolo de autorizacao: 113170005427466\n");
        BluetoothPrintDriver.BT_Write("Data de autorizacao: 14/09/17 08:40:52\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();

        try {
            Bitmap bmp = encodeToQrCode("Hello world - Compufacil", 200, 200);
            if (bmp != null ) {
                byte[] command = Utils.decodeBitmap(bmp);
                BluetoothPrintDriver.BT_Write(command);
            }else{
                Log.e("Print Photo error", "the file isn't exists");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrintTools", "the file isn't exists");
        }

        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.BT_Write("\nTributos Totais Incidentes(Lei Federal 12.741/2012)R$2,50\n");
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
        BluetoothPrintDriver.LF();
        BluetoothPrintDriver.CR();
    }


    public Bitmap encodeToQrCode(String text, int width, int height){
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = null;
        try {
            matrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height);
        } catch (WriterException ex) {
            ex.printStackTrace();
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++){
            for (int y = 0; y < height; y++){
                bmp.setPixel(x, y, matrix.get(x,y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth não ligado", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);

            BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

            mChatService.connect(device);
            deviceNameStr = device.getName();
            mBTArrayAdapter.clear(); // clear items
            devicesDialog.hide();
        }
    };

    private AdapterView.OnItemClickListener mockListClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            printNFCe(v);
        }
    };
}
