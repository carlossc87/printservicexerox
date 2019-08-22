package io.github.carlossc87.printservicexerox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class XeroxPrinterDiscoverySession extends PrinterDiscoverySession {

    private static final String LOG_TAG = "XeroxPrintService";

    private final PrinterId printerId;
    private final Context context;

    public XeroxPrinterDiscoverySession(PrinterId printerId, Context context){
        Log.d(LOG_TAG, "#XeroxPrinterDiscoverySession()");

        this.printerId = printerId;
        this.context = context;
    }

    @Override
    public void onStartPrinterDiscovery(List<PrinterId> list) {
        Log.d(LOG_TAG, "#onStartPrinterDiscovery()");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("host","192.168.1.2");

        Boolean disponible = true;
        try{
            disponible = new AsyncTask<Void, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        final OkHttpClient client = new OkHttpClient.Builder()
                                .build();

                        Request request = new Request.Builder()
                                .url("http://"+host+"/print.htm")
                                .get()
                                .build();

                        try (Response response = client.newCall(request).execute()) {
                            if (!response.isSuccessful()) {
                                Log.e(LOG_TAG, "La impresora ha contestado con un codigo HTTP distinto de 200, no está disponible.");
                                return Boolean.FALSE;
                            }

                            Log.i(LOG_TAG, "La impresora esta disponible.");
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Se ha producido un error comprobar que la impresora esta disponible.", e);
                        return Boolean.FALSE;
                    }

                    return Boolean.TRUE;
                }
            }.execute().get();
        }catch(ExecutionException | InterruptedException e){
            disponible = false;
        }

        if(!disponible){
            return;
        }

        final PrinterCapabilitiesInfo capabilities =
                new PrinterCapabilitiesInfo.Builder(printerId)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                        .addResolution(new PrintAttributes.Resolution("1","Normal",
                                600,600), true)
                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR |
                                        PrintAttributes.COLOR_MODE_MONOCHROME,
                                PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE |
                                        PrintAttributes.DUPLEX_MODE_LONG_EDGE |
                                        PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
                                PrintAttributes.DUPLEX_MODE_NONE)
                        .build();

        final PrinterInfo printerInfo = new PrinterInfo.Builder(printerId,
                "Xerox WorkCentre 7425", PrinterInfo.STATUS_IDLE)
                .setCapabilities(capabilities)
                .build();

        addPrinters(new ArrayList<PrinterInfo>() {{
            add(printerInfo);
        }});
    }

    @Override
    public void onStopPrinterDiscovery() {
        Log.d(LOG_TAG, "#onStopPrinterDiscovery()");
    }

    @Override
    public void onValidatePrinters(List<PrinterId> list) {
        Log.d(LOG_TAG, "#onValidatePrinters()");
    }

    @Override
    public void onStartPrinterStateTracking(PrinterId printerId) {
        Log.d(LOG_TAG, "#onStartPrinterStateTracking()");
    }

    @Override
    public void onStopPrinterStateTracking(PrinterId printerId) {
        Log.d(LOG_TAG, "#onStopPrinterStateTracking()");
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "#onDestroy()");
    }
}
