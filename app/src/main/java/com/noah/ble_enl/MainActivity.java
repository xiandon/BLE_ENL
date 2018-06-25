package com.noah.ble_enl;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.noah.ble_enl.event.ConnectEvent;
import com.noah.ble_enl.event.NotifyDataEvent;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.vise.baseble.ViseBle;
import com.vise.baseble.callback.scan.IScanCallback;
import com.vise.baseble.callback.scan.ScanCallback;
import com.vise.baseble.common.PropertyType;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.model.BluetoothLeDeviceStore;
import com.vise.baseble.model.resolver.GattAttributeResolver;
import com.vise.baseble.utils.HexUtil;
import com.vise.log.ViseLog;
import com.vise.log.inner.LogcatTree;
import com.vise.xsnow.cache.SpCache;
import com.vise.xsnow.event.BusManager;
import com.vise.xsnow.event.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * author       Noah
 * create       2018/6/25
 * method       MainActivity.java
 * desc         程序入口
 */
public class MainActivity extends AppCompatActivity {

    private static final String LIST_NAME = "NAME_ENL";
    private static final String LIST_UUID = "UUID_ENL";
    public static final String WRITE_CHARACTERISTI_UUID_KEY = "write_uuid_key_ENL";
    public static final String NOTIFY_CHARACTERISTIC_UUID_KEY = "notify_uuid_key_ENL";
    public static final String WRITE_DATA_KEY = "write_data_key_ENL";

    //设备扫描结果展示适配器
    private DeviceAdapter adapter;
    private ListView lv_ble_scan;
    private TextView tv_ble_scan_count;
    private TextView tv_rec_data;
    private Button btn_scan_ble;
    private Button btn_bind_channel;
    private boolean bScanBle = false;

    private BluetoothLeDeviceStore bluetoothLeDeviceStore = new BluetoothLeDeviceStore();
    private String TAG = "主程序";

    private BluetoothLeDevice device;
    //输出数据展示
    private StringBuilder mOutputInfo = new StringBuilder();

    private SimpleExpandableListAdapter simpleExpandableListAdapter;
    private List<BluetoothGattService> mGattServices = new ArrayList<>();
    //设备特征值集合
    private List<List<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private SpCache mSpCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBLE2();
        requestPermissions();
        initView();

    }

    /**
     * time    2018/6/25 14:24
     * desc    视图初始化
     */
    private void initView() {
        mSpCache = new SpCache(this);
        lv_ble_scan = findViewById(R.id.lv_ble_scan);
        tv_ble_scan_count = findViewById(R.id.tv_ble_scan_count);
        tv_rec_data = findViewById(R.id.tv_rec_data);
        btn_scan_ble = findViewById(R.id.btn_scan_ble);
        btn_bind_channel = findViewById(R.id.btn_bind_channel);

        adapter = new DeviceAdapter(this);
        lv_ble_scan.setAdapter(adapter);

        lv_ble_scan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // todo 连接蓝牙操作
                // 获取蓝牙设备
                device = (BluetoothLeDevice) adapter.getItem(position);
                if (device == null) return;
                connectDevice(device);
                Log.i(TAG, "连接设备");
            }
        });

        btn_scan_ble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bScanBle) {
                    stopScan();
                    showLog("===停止扫描===");
                    btn_scan_ble.setText("开始扫描");
                    bScanBle = false;
                } else {
                    startScan();
                    showLog("===开始扫描===");
                    btn_scan_ble.setText("停止扫描");
                    bScanBle = true;
                }
            }
        });

        btn_bind_channel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGattServices();
            }
        });
    }


    /**
     * time    2018/6/25 15:12
     * desc    断开设备连接
     */
    private void disConnectDevice(BluetoothLeDevice device) {
        if (BluetoothDeviceManager.getInstance().isConnected(device)) {
            BluetoothDeviceManager.getInstance().disconnect(device);
            invalidateOptionsMenu();
        }
    }

    /**
     * time    2018/6/25 15:13
     * desc    连接设备
     */
    private void connectDevice(BluetoothLeDevice device) {
        if (!BluetoothDeviceManager.getInstance().isConnected(device)) {
            BluetoothDeviceManager.getInstance().connect(device);
            showLog("设备连接");
        }
    }

    @Subscribe
    public void showConnectedDevice(ConnectEvent event) {
        if (event != null) {
            if (event.isSuccess()) {
                showLog("Connect Success!");
                if (event.getDeviceMirror() != null && event.getDeviceMirror().getBluetoothGatt() != null) {
                    simpleExpandableListAdapter = displayGattServices(event.getDeviceMirror().getBluetoothGatt().getServices());
                }
            } else {
                if (event.isDisconnected()) {
                    showLog("Disconnect!");
                } else {
                    showLog("Connect Failure!");
                }
            }
        }
    }

    private SimpleExpandableListAdapter displayGattServices(final List<BluetoothGattService> gattServices) {
        if (gattServices == null) return null;
        String uuid;
        final String unknownServiceString = getResources().getString(R.string.unknown_service);
        final String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        final List<Map<String, String>> gattServiceData = new ArrayList<>();
        final List<List<Map<String, String>>> gattCharacteristicData = new ArrayList<>();

        mGattServices = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (final BluetoothGattService gattService : gattServices) {
            final Map<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, GattAttributeResolver.getAttributeName(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            final List<Map<String, String>> gattCharacteristicGroupData = new ArrayList<>();
            final List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            final List<BluetoothGattCharacteristic> charas = new ArrayList<>();

            // Loops through available Characteristics.
            for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                final Map<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, GattAttributeResolver.getAttributeName(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }

            mGattServices.add(gattService);
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        final SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(this, gattServiceData, android.R.layout
                .simple_expandable_list_item_2, new String[]{LIST_NAME, LIST_UUID}, new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData, android.R.layout.simple_expandable_list_item_2, new String[]{LIST_NAME, LIST_UUID}, new
                int[]{android.R.id.text1, android.R.id.text2});
        return gattServiceAdapter;
    }


    /**
     * time    2018/6/25 16:04
     * desc    显示GATT服务展示的信息
     */
    private void showGattServices() {
        if (simpleExpandableListAdapter == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_gatt_services, null);
        ExpandableListView expandableListView = (ExpandableListView) view.findViewById(R.id.dialog_gatt_services_list);
        expandableListView.setAdapter(simpleExpandableListAdapter);
        builder.setView(view);
        final AlertDialog dialog = builder.show();
        expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                dialog.dismiss();
                final BluetoothGattService service = mGattServices.get(groupPosition);
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    mSpCache.put(WRITE_CHARACTERISTI_UUID_KEY + device.getAddress(), characteristic.getUuid().toString());
                    ((EditText) findViewById(R.id.tv_show_write_characteristic)).setText(characteristic.getUuid().toString());
                    BluetoothDeviceManager.getInstance().bindChannel(device, PropertyType.PROPERTY_WRITE, service.getUuid(), characteristic.getUuid(), null);
                } else if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    BluetoothDeviceManager.getInstance().bindChannel(device, PropertyType.PROPERTY_READ, service.getUuid(), characteristic.getUuid(), null);
                    BluetoothDeviceManager.getInstance().read(device);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mSpCache.put(NOTIFY_CHARACTERISTIC_UUID_KEY + device.getAddress(), characteristic.getUuid().toString());
                    ((EditText) findViewById(R.id.tv_show_write_characteristic)).setText(characteristic.getUuid().toString());
                    BluetoothDeviceManager.getInstance().bindChannel(device, PropertyType.PROPERTY_NOTIFY, service.getUuid(), characteristic.getUuid(), null);
                    BluetoothDeviceManager.getInstance().registerNotify(device, false);
                } else if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    mSpCache.put(NOTIFY_CHARACTERISTIC_UUID_KEY + device.getAddress(), characteristic.getUuid().toString());
                    ((EditText) findViewById(R.id.tv_show_write_characteristic)).setText(characteristic.getUuid().toString());
                    BluetoothDeviceManager.getInstance().bindChannel(device, PropertyType.PROPERTY_INDICATE, service.getUuid(), characteristic.getUuid(), null);
                    BluetoothDeviceManager.getInstance().registerNotify(device, true);
                }

                return true;
            }
        });
    }

    @Subscribe
    public void showDeviceNotifyData(NotifyDataEvent event) {
        if (event != null && event.getData() != null && event.getBluetoothLeDevice() != null
                && event.getBluetoothLeDevice().getAddress().equals(device.getAddress())) {
            showLog(HexUtil.encodeHexStr(event.getData()));
            tv_rec_data.setText(HexUtil.encodeHexStr(event.getData()));
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
        bluetoothLeDeviceStore.clear();
    }

    /**
     * time    2018/6/25 14:35
     * desc    停止扫描
     */
    private void stopScan() {
        ViseBle.getInstance().stopScan(allScanCallBack);
    }

    /**
     * time    2018/6/25 14:27
     * desc    开始扫描
     */
    private void startScan() {
        updateItemCount(0);
        // 设置数据列表为空
        if (adapter != null) {
            adapter.setListAll(new ArrayList<BluetoothLeDevice>());
        }

        ViseBle.getInstance().startScan(allScanCallBack);
        invalidateOptionsMenu();
    }

    /**
     * time    2018/6/25 14:26
     * desc    输出log信息
     */
    private void showLog(String s) {
        Log.i("主程序 ", s);
    }


    private ScanCallback allScanCallBack = new ScanCallback(new IScanCallback() {
        @Override
        public void onDeviceFound(BluetoothLeDevice bluetoothLeDevice) {
            // 找到设备
            bluetoothLeDeviceStore.addDevice(bluetoothLeDevice);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null && bluetoothLeDeviceStore != null) {
                        adapter.setListAll(bluetoothLeDeviceStore.getDeviceList());
                        updateItemCount(adapter.getCount());
                    }
                }
            });
        }

        @Override
        public void onScanFinish(BluetoothLeDeviceStore bluetoothLeDeviceStore) {
            // 扫描结束
            showLog("扫描结束");
        }

        @Override
        public void onScanTimeout() {
            // 扫描超时
            showLog("扫描超时");
        }
    });

    private void updateItemCount(int count) {
        tv_ble_scan_count.setText(getString(R.string.formatter_item_count, String.valueOf(count)));
    }


    /**
     * time    2018/6/25 14:03
     * desc    初始化蓝牙配置
     */
    private void initBLE2() {
        ViseLog.getLogConfig().configAllowLog(true);//配置日志信息
        ViseLog.plant(new LogcatTree());//添加Logcat打印信息
        BluetoothDeviceManager.getInstance().init(this);
        BusManager.getBus().register(this);
    }


    /**
     * time    2018/6/25 14:57
     * desc    权限申请
     */
    private void requestPermissions() {
        RxPermissions rxPermission = new RxPermissions(this);
        rxPermission.requestEach(
                Manifest.permission.INTERNET,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_SETTINGS
        ).subscribe(new io.reactivex.functions.Consumer<Permission>() {
            @Override
            public void accept(Permission permission) {
                if (permission.granted) {
                    // 用户已经同意该权限
                    Log.d(TAG, permission.name + " is granted.");
                } else if (permission.shouldShowRequestPermissionRationale) {
                    // 用户拒绝了该权限，没有选中『不再询问』（Never ask again）,那么下次再次启动时，还会提示请求权限的对话框
                    Log.d(TAG, permission.name + " is denied. More info should be provided.");
                } else {
                    // 用户拒绝了该权限，并且选中『不再询问』
                    Log.d(TAG, permission.name + " is denied.");
                }
            }
        });
    }
}
