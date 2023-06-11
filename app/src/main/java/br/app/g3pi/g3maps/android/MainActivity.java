package br.app.g3pi.g3maps.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int REQUEST_PERMISSIONS = 3;

    private ValueCallback<Uri[]> fileCallback;
    int IMG_CAPTURE_REQUEST = 1000;
    int FILE_REQUEST = 2000;
    String CAMERA_FILE_PATH, INTENT_PHOTO = "intent_photo";
    String currentPhotoPath;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        registerForContextMenu(webView);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> valueCallback,
                    WebChromeClient.FileChooserParams fileChooserParams) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = valueCallback;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // Ensure that there's a camera activity to handle the intent
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        Log.e("createImageFile", "Falha ao criar arquivo: " + photoFile.getAbsolutePath());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                "br.app.g3pi.g3maps.android",
                                photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("image/*");

                Intent[] _arryIntent;
                if (takePictureIntent != null) {
                    _arryIntent = new Intent[]{takePictureIntent};
                } else {
                    _arryIntent = new Intent[0];
                }
                Intent makeChoiceIntent = new Intent(Intent.ACTION_CHOOSER);
                makeChoiceIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                makeChoiceIntent.putExtra(Intent.EXTRA_TITLE, "Escolher imagem");
                makeChoiceIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, _arryIntent);

                startActivityIntent.launch(makeChoiceIntent);

                return true;
            }
        });

        LinearLayout loadScreen = findViewById(R.id.progressbar);
        loadScreen.setVisibility(View.GONE);

        LinearLayout noConnectionLayout = findViewById(R.id.noconnection);
        noConnectionLayout.setVisibility(View.GONE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tg://")) {
                    // Manipule a URI personalizada aqui
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true; // Evite que o WebView abra a URI
                }
                return false; // Deixe o WebView lidar com a URL padrão
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // Exibe a ProgressBar
                loadScreen.setVisibility(View.VISIBLE);
                noConnectionLayout.setVisibility(View.GONE);
            }

            // Evento chamado quando o WebView termina de carregar uma página
            @Override
            public void onPageFinished(WebView view, String url) {
                // Esconde a ProgressBar
                loadScreen.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT
                        || errorCode == WebViewClient.ERROR_TIMEOUT || errorCode == WebViewClient.ERROR_UNKNOWN) {
                    // Houve um erro de conexão, mostre o LinearLayout de "Sem conexão"
                    noConnectionLayout.setVisibility(View.VISIBLE);
                } else {
                    // Outro tipo de erro, oculte o LinearLayout de "Sem conexão"
                    noConnectionLayout.setVisibility(View.GONE);
                }
            }
        });

        // Solicitar permissões em tempo de execução (Android 6.0+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        } else {
            setupWebView();
        }
    }

    private static final long DOUBLE_BACK_PRESS_DURATION = 2000; // Tempo limite para duplo clique em milissegundos
    private long backPressTimestamp = 0; // Timestamp do último clique em voltar

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            String currentUrl = webView.getUrl();
            if (currentUrl != null && (
                    currentUrl.equals("https://g3mapsapp.netlify.app") ||
                            currentUrl.equals("https://g3mapsapp.netlify.app/index.html") ||
                            currentUrl.equals("http://g3mapsapp.netlify.app") ||
                            currentUrl.equals("http://g3mapsapp.netlify.app/index.html"))) {
                long currentTimestamp = System.currentTimeMillis();
                if (currentTimestamp - backPressTimestamp <= DOUBLE_BACK_PRESS_DURATION) {
                    super.onBackPressed();
                } else {
                    Toast.makeText(this, "Pressione novamente para sair.", Toast.LENGTH_SHORT).show();
                    backPressTimestamp = currentTimestamp;
                }
            } else {
                webView.goBack();
            }
        } else {
            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - backPressTimestamp <= DOUBLE_BACK_PRESS_DURATION) {
                super.onBackPressed();
            } else {
                Toast.makeText(this, "Pressione novamente para sair.", Toast.LENGTH_SHORT).show();
                backPressTimestamp = currentTimestamp;
            }
        }
    }
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Get the directory where the image will be stored
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null || !storageDir.exists()) {
            storageDir = new File(Environment.getExternalStorageDirectory(), "Pictures");
        }

        // Create the image file
        File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);

        // Save the file path for use with ACTION_VIEW intents
        currentPhotoPath = imageFile.getAbsolutePath();
        return imageFile;
    }
    ActivityResultLauncher<Intent> startActivityIntent = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri[] results;
                    if (result.getData() == null || result.getData().getData() == null) {
                        // Image was captured with the camera
                        Uri photoUri = Uri.fromFile(new File(currentPhotoPath));
                        results = new Uri[]{photoUri};
                    } else {
                        // Image was selected from the gallery
                        Uri galleryUri = result.getData().getData();
                        results = new Uri[]{galleryUri};
                    }

                    fileCallback.onReceiveValue(results);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Imagem selecionada com sucesso.", Toast.LENGTH_LONG).show();
                }
            });

    public void refreshWeb(View v) {
        webView.reload();
    }

    @SuppressLint("MissingPermission")
    private void setupWebView() {
        // Configurar o WebView com as permissões concedidas
        webView.loadUrl("https://g3mapsapp.netlify.app");

        // Obter a localização do usuário (GPS)
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Atualizar a localização no WebView
                String javascript = String.format("javascript:setCurrentLocation(%f, %f)", location.getLatitude(), location.getLongitude());
                webView.evaluateJavascript(javascript, null);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        // Solicitar atualizações de localização
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                setupWebView();
            } else {
                // Alguma permissão não foi concedida
                // Lide com isso de acordo com seus requisitos
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}