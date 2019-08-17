package io.github.carlossc87.printservicexerox;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.print.PrintAttributes;
import android.print.PrintJobInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class XeroxPrintService extends PrintService {

    private static final String LOG_TAG = "XeroxPrintService";

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        Log.d(LOG_TAG, "#onCreatePrinterDiscoverySession()");

        return new XeroxPrinterDiscoverySession(generatePrinterId("xerox-workcentre-7425"), this.getApplicationContext());
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        Log.d(LOG_TAG, "#onRequestCancelPrintJob()");
    }

    @Override
    protected void onPrintJobQueued(final PrintJob printJob) {
        Log.d(LOG_TAG, "#onPrintJobQueued()");

        if (!printJob.isQueued()) {
            return;
        }

        Log.i(LOG_TAG, "Iniciando trabajo de impresion.");
        printJob.start();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final String host = prefs.getString("host","192.168.1.2");

        // Establecemos el modo de color
        final String color;
        switch (printJob.getInfo().getAttributes().getColorMode()){
            case PrintAttributes.COLOR_MODE_COLOR:
                color = "CLR";
                break;
            case PrintAttributes.COLOR_MODE_MONOCHROME:
            default:
                color = "BW";
        }

        // Establecemos el modo doble cara
        final String dobleCara;
        switch (printJob.getInfo().getAttributes().getDuplexMode()){
            case PrintAttributes.DUPLEX_MODE_SHORT_EDGE:
                dobleCara = "TB";
                break;
            case PrintAttributes.DUPLEX_MODE_LONG_EDGE:
                dobleCara = "DP";
                break;
            case PrintAttributes.DUPLEX_MODE_NONE:
            default:
                dobleCara = "NO";
        }

        // Obtenemos el fichero
        final byte file[];
        try (ByteArrayOutputStream buff = new ByteArrayOutputStream();
             InputStream fileStream = new FileInputStream(
                     printJob.getDocument().getData().getFileDescriptor())) {
            int length;
            byte[] buffer = new byte[1024];
            while((length = fileStream.read(buffer)) > 0) {
                buff.write(buffer, 0, length);
            }
            buff.flush();
            file = buff.toByteArray();
        } catch (IOException e) {
            Log.e(LOG_TAG, "No se ha encontrado el documento.", e);
            printJob.fail("No se ha encontrado el documento.");
            return;
        }

        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    final OkHttpClient client = new OkHttpClient.Builder()
                            .build();

                    RequestBody requestBody = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("ESPID", "off")
                            .addFormDataPart("CPN", "1")
                            .addFormDataPart("COLT", "NO")
                            .addFormDataPart("OT", "CT2")
                            .addFormDataPart("IT", "AUTO")
                            .addFormDataPart("SIZ", "NUL")
                            .addFormDataPart("MED", "NUL")
                            .addFormDataPart("DEL", "IMP")
                            .addFormDataPart("PPUSR", "")
                            .addFormDataPart("HOUR", "")
                            .addFormDataPart("MIN", "")
                            .addFormDataPart("SPUSR", "")
                            .addFormDataPart("SPID", "************")
                            .addFormDataPart("RSPID", "************")
                            .addFormDataPart("CLR", color)
                            .addFormDataPart("DUP", dobleCara)
                            .addFormDataPart("FILE", "file.pdf",
                                    RequestBody.create(MediaType.parse("application/pdf"), file))
                            .build();

                    Request request = new Request.Builder()
                            .url("http://"+host+"/UPLPRT.cmd")
                            .post(requestBody)
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            Log.e(LOG_TAG, "La impresora ha contestado con un codigo HTTP distinto de 200.");
                            return Boolean.FALSE;
                        }

                        Log.i(LOG_TAG, "El documento se ha enviado correctamente a la impresora.");
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Se ha producido un error al mandar el documento.", e);
                    return Boolean.FALSE;
                }

                return Boolean.TRUE;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if(result != null && result){
                    printJob.complete();
                }else{
                    printJob.fail("No se ha podido enviar el documento a la impresora.");
                }
            }
        }.execute();

    }
}
