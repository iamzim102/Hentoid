package me.devsaki.hentoid.db;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by neko on 15/06/2015.
 */
public class TestHentoidDB extends AndroidTestCase {

    boolean locker1, locker2, locker3, locker4;

    public void testLock() {
        List<Content> contents = generateRandomContent();

        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
        HentoidDB db = new HentoidDB(context);
        db.insertContents(contents.toArray(new Content[contents.size()]));
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = new HentoidDB(context);
                        for (int i = 0; i < 100; i++) {
                            List<Content> contents = generateRandomContent();
                            db.insertContents(contents.toArray(new Content[contents.size()]));
                        }
                    } catch (Exception ex) {
                        Log.e("error", "error", ex);
                    }
                    locker1 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = new HentoidDB(context);
                        for (int i = 0; i < 100; i++) {
                            List<Content> contents = generateRandomContent();
                            db.insertContents(contents.toArray(new Content[contents.size()]));
                        }
                    } catch (Exception ex) {
                        Log.e("error", "error", ex);
                    }
                    locker2 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = new HentoidDB(context);
                        for (int i = 0; i < 100; i++) {
                            db.selectContentByQuery("", 1, 10, false);
                        }
                    } catch (Exception ex) {
                        Log.e("error", "error", ex);
                    }
                    locker3 = true;
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
                        HentoidDB db = new HentoidDB(context);
                        for (int i = 0; i < 100; i++) {
                            db.selectContentByStatus(StatusContent.DOWNLOADED);
                        }
                    } catch (Exception ex) {
                        Log.e("error", "error", ex);
                    }
                    locker4 = true;
                }
            }).start();
            //noinspection StatementWithEmptyBody
            while (!(locker1 && locker2 && locker3 && locker4)) ;
            Log.i("Test DB lock", "Success");
        } catch (Exception ex) {
            Log.e("test DB lock", "error", ex);
        }
    }

    private List<Content> generateRandomContent() {
        List<Content> contents = new ArrayList<>();
        Random randomGenerator = new Random();
        for (int i = 0; i < 10; i++) {
            int k = randomGenerator.nextInt();
            Content content = new Content();
            content.setAttributes(new AttributeMap());
            for (AttributeType type : AttributeType.values()) {
                List<Attribute> attributes = new ArrayList<>();
                for (int j = 0; j < 10; j++) {
                    int l = randomGenerator.nextInt();
                    Attribute attribute = new Attribute();
                    attribute.setUrl("" + l);
                    attribute.setName("n" + l);
                    attribute.setType(type);
                    attributes.add(attribute);
                }
                content.getAttributes().put(type, attributes);
            }
            content.setUrl("/doujinshi/u" + k);
            content.setCoverImageUrl("c" + k);
            content.setDownloadable(false);
            content.setDownloadDate(1000 * k);
            content.setHtmlDescription("html " + k);
            content.setPercent(10.0 * k);
            content.setQtyPages(k * 12);
            content.setTitle("t " + k);
            content.setStatus(StatusContent.DOWNLOADED);
            content.setUploadDate(k * 2000);
            contents.add(content);
        }
        return contents;
    }
}
