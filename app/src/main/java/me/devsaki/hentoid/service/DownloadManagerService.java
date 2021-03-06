package me.devsaki.hentoid.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.DownloadManagerActivity;
import me.devsaki.hentoid.DownloadsActivity;
import me.devsaki.hentoid.MainActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.parser.HitomiParser;
import me.devsaki.hentoid.parser.NhentaiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.NetworkStatus;

public class DownloadManagerService extends IntentService {

    public static final String INTENT_PERCENT_BROADCAST = "broadcast_percent";
    public static final String NOTIFICATION = "me.devsaki.hentoid.service";
    private static final String TAG = DownloadManagerService.class.getName();
    private static int notificationID  = -1;
    public static boolean paused;
    private NotificationManager notificationManager;
    private HentoidDB db;
    /**
     * Used to keep track for notification purposes
     */
    private static ArrayList<String> hChaptersDownloaded = new ArrayList<String>();

    public DownloadManagerService() {
        super(DownloadManagerService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        db = new HentoidDB(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Content content = db.selectContentByStatus(StatusContent.DOWNLOADING);
        hChaptersDownloaded = intent.getStringArrayListExtra("names");
        notificationID = intent.getIntExtra("ID", 0);
        if (content == null || content.getStatus() == StatusContent.DOWNLOADED)
            return;

        showNotification(0, content);
        if (paused) {
            paused = false;
            content = db.selectContentById(content.getId());
            showNotification(0, content);
            return;
        }

        try {
            parseImageFiles(content);
        } catch (Exception e) {
            content.setStatus(StatusContent.UNHANDLED_ERROR);
            showNotification(0, content);
            content.setStatus(StatusContent.PAUSED);
            db.updateContentStatus(content);
            updateActivity(-1);
            return;
        }

        if (paused) {
            paused = false;
            content = db.selectContentById(content.getId());
            showNotification(0, content);
            return;
        }

        Log.i(TAG, "Start Download Content : " + content.getTitle());

        boolean error = false;
        //Directory
        File dir = Helper.getDownloadDir(content, this);
        try {
            //Download Cover Image
            Helper.saveInStorage(new File(dir, "thumb.jpg"), content.getCoverImageUrl());
        } catch (Exception e) {
            Log.e(TAG, "Error Saving cover image " + content.getTitle(), e);
            error = true;
        }


        int count = 0;
        for (ImageFile imageFile : content.getImageFiles()) {
            if (paused) {
                paused = false;
                content = db.selectContentById(content.getId());
                showNotification(0, content);
                if (content.getStatus() == StatusContent.SAVED) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        Log.e(TAG, "error deleting content directory", e);
                    }
                }
                return;
            }
            boolean imageFileErrorDownload = false;
            try {
                if (imageFile.getStatus() != StatusContent.IGNORED) {
                    if (!NetworkStatus.isOnline(this))
                        throw new Exception("Not connection");
                    Helper.saveInStorage(new File(dir, imageFile.getName()), imageFile.getUrl());
                    Log.i(TAG, "Download Image File (" + imageFile.getName() + ") / "
                            + content.getTitle());
                }
                count++;
                double percent = count * 100.0 / content.getImageFiles().size();
                showNotification(percent, content);
                updateActivity(percent);
            } catch (Exception ex) {
                Log.e(TAG, "Error Saving Image File (" + imageFile.getName() + ") "
                        + content.getTitle(), ex);
                error = true;
                imageFileErrorDownload = true;
            }
            if (imageFileErrorDownload) {
                imageFile.setStatus(StatusContent.ERROR);
            } else {
                imageFile.setStatus(StatusContent.DOWNLOADED);
            }
            db.updateImageFileStatus(imageFile);
        }
        db.updateContentStatus(content);
        content.setDownloadDate(new Date().getTime());
        if (error) {
            content.setStatus(StatusContent.ERROR);
        } else {
            content.setStatus(StatusContent.DOWNLOADED);
        }
        //Save JSON file
        try {
            Helper.saveJson(content, dir);
        } catch (IOException e) {
            Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
        }
        db.updateContentStatus(content);
        Log.i(TAG, "Finish Download Content : " + content.getTitle());
        showNotification(0, content);
        updateActivity(-1);
        content = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (content != null) {
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this,
                    DownloadManagerService.class);
            intentService.putExtra("content_id", content.getId());
            startService(intentService);
        }
    }

    private void updateActivity(double percent) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(INTENT_PERCENT_BROADCAST, percent);
        sendBroadcast(intent);
    }

    private void showNotification(double percent, Content content) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                this).setSmallIcon(
                content.getSite().getIco()).setContentTitle(content.getTitle());
        int resource = 0;
        mBuilder.setLocalOnly(true);

        Intent resultIntent = null;
        if (content.getStatus() ==
                StatusContent.DOWNLOADED || content.getStatus() == StatusContent.ERROR
                || content.getStatus() == StatusContent.UNHANDLED_ERROR) {
            resultIntent = new Intent(this,
                    DownloadsActivity.class);
        } else if (content.getStatus() == StatusContent.DOWNLOADING
                || content.getStatus() == StatusContent.PAUSED) {
            resultIntent = new Intent(this,
                    DownloadManagerActivity.class);
        } else if (content.getStatus() == StatusContent.SAVED) {
            resultIntent = new Intent(this,
                    MainActivity.class);
            resultIntent.putExtra("url", content.getUrl());
        }

        // Adds the Intent to the top of the stack
        // Gets a PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this,
                0, resultIntent, PendingIntent.FLAG_ONE_SHOT);

        if (content.getStatus() ==  StatusContent.DOWNLOADED && hChaptersDownloaded.size()>1 ){
                            mBuilder.setContentTitle(hChaptersDownloaded.size() + " Chapters downloaded");
                String text = null;

                for(int i = 0; i < hChaptersDownloaded.size(); i++)
                {
                    if (i == 0) {
                        text = hChaptersDownloaded.get(i);
                    }else{
                        text += ", " + hChaptersDownloaded.get(i);
                    }
                }
                mBuilder.setContentText(text);
                notify(mBuilder, notificationID, percent, resultPendingIntent );
                return;
        }


        if (content.getStatus() == StatusContent.DOWNLOADING) {
            mBuilder.setContentText(getResources().getString(R.string.downloading)
                    + String.format("%.2f", percent) + "%");
            mBuilder.setProgress(100, (int) percent, percent == 0);
        } else {

            if (content.getStatus() == StatusContent.DOWNLOADED) {
                resource = R.string.download_completed;
            } else if (content.getStatus() == StatusContent.PAUSED) {
                resource = R.string.download_paused;
            } else if (content.getStatus() == StatusContent.SAVED) {
                resource = R.string.download_cancelled;
            } else if (content.getStatus() == StatusContent.ERROR) {
                resource = R.string.download_error;
            } else if (content.getStatus() == StatusContent.UNHANDLED_ERROR) {
                resource = R.string.unhandled_download_error;
            }
            mBuilder.setContentText(getResources().getString(resource));
            mBuilder.setProgress(0, 0, false);
        }
        notify(mBuilder, notificationID, percent, resultPendingIntent );
    }
    private void notify(NotificationCompat.Builder mBuilder, int ID, double percent, PendingIntent resultPendingIntent)
    {
        Notification notif = mBuilder.build();
        notif.contentIntent = resultPendingIntent;
        if (percent > 0){

            notif.flags = Notification.FLAG_ONGOING_EVENT;}

        else{

            notif.flags = notif.flags | Notification.DEFAULT_LIGHTS
                    | Notification.FLAG_AUTO_CANCEL;}
        notificationManager.notify(notificationID, notif);


    }
    private void parseImageFiles(Content content) throws Exception {
        content.setImageFiles(new ArrayList<ImageFile>());
        List<String> aUrls = new ArrayList<>();
        try {
            if (content.getSite() == Site.HITOMI) {
                String html = HttpClientHelper.call(content.getReaderUrl());
                aUrls = HitomiParser.parseImageList(html);
            } else if (content.getSite() == Site.NHENTAI) {
                String json = HttpClientHelper.call(content.getGalleryUrl() + "/json");
                aUrls = NhentaiParser.parseImageList(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting image urls", e);
            throw e;
        }

        int i = 1;
        for (String str : aUrls) {
            String name = String.format("%03d", i) + ".jpg";
            ImageFile imageFile = new ImageFile();
            imageFile.setUrl(str);
            imageFile.setOrder(i++);
            imageFile.setStatus(StatusContent.SAVED);
            imageFile.setName(name);
            content.getImageFiles().add(imageFile);
        }
        db.insertImageFiles(content);
    }
}