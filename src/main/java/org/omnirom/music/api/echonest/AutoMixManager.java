package org.omnirom.music.api.echonest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.IPlaybackService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by Guigui on 14/08/2014.
 */
public class AutoMixManager {
    private static final String TAG = "AutoMixManager";

    private static final String SHARED_PREFS = "automix_buckets";
    private static final String PREF_BUCKETS_IDS = "buckets_ids";
    private static final String PREF_PREFIX_NAME = "bucket_name_";
    private static final String PREF_PREFIX_STYLES = "bucket_styles_";
    private static final String PREF_PREFIX_MOODS = "bucket_moods_";
    private static final String PREF_PREFIX_TASTE = "bucket_taste_";
    private static final String PREF_PREFIX_ADVENTUROUS = "bucket_adventurous_";
    private static final String PREF_PREFIX_SONG_TYPES = "bucket_song_types_";
    private static final String PREF_PREFIX_SPEECHINESS = "bucket_speechiness_";
    private static final String PREF_PREFIX_ENERGY = "bucket_energy_";
    private static final String PREF_PREFIX_FAMILIAR = "bucket_familiar_";

    private Context mContext;
    private List<AutoMixBucket> mBuckets;
    private AutoMixBucket mCurrentPlayingBucket;

    public AutoMixManager(Context ctx) {
        mContext = ctx;
        mBuckets = new ArrayList<AutoMixBucket>();
        readBucketsFromPrefs();
    }

    public void readBucketsFromPrefs() {
        SharedPreferences prefs = mContext.getSharedPreferences(SHARED_PREFS, 0);
        Set<String> buckets = prefs.getStringSet(PREF_BUCKETS_IDS, new TreeSet<String>());

        mBuckets.clear();

        for (String bucketId : buckets) {
            AutoMixBucket bucket = restoreBucketFromId(bucketId);
            mBuckets.add(bucket);
        }
    }

    public AutoMixBucket createBucket(String name, String[] styles, String[] moods, boolean taste,
                                      float adventurous, String[] songTypes, float speechiness,
                                      float energy, float familiar) {

        AutoMixBucket bucket = new AutoMixBucket(name, styles, moods, taste, adventurous, songTypes,
                speechiness, energy, familiar);
        bucket.createPlaylistSession();
        mBuckets.add(bucket);
        saveBucket(bucket);
        return bucket;
    }

    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(SHARED_PREFS, 0);
    }

    private void saveBucket(AutoMixBucket bucket) {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        final String id = bucket.getSessionId();

        editor.putString(PREF_PREFIX_NAME       + id, bucket.mName);
        editor.putFloat(PREF_PREFIX_ADVENTUROUS + id, bucket.mAdventurousness);
        editor.putFloat(PREF_PREFIX_ENERGY      + id, bucket.mEnergy);
        editor.putFloat(PREF_PREFIX_FAMILIAR    + id, bucket.mFamiliar);
        editor.putString(PREF_PREFIX_MOODS      + id, Utils.implode(bucket.mMoods, ","));
        editor.putString(PREF_PREFIX_SONG_TYPES + id, Utils.implode(bucket.mSongTypes, ","));
        editor.putFloat(PREF_PREFIX_SPEECHINESS + id, bucket.mSpeechiness);
        editor.putString(PREF_PREFIX_STYLES     + id, Utils.implode(bucket.mStyles, ","));
        editor.putBoolean(PREF_PREFIX_TASTE     + id, bucket.mUseTaste);

        editor.apply();
    }

    public AutoMixBucket restoreBucketFromId(final String id) {
        SharedPreferences prefs = getPrefs();

        return new AutoMixBucket(
                prefs.getString(PREF_PREFIX_NAME + id, null),
                prefs.getString(PREF_PREFIX_STYLES + id, "").split(","),
                prefs.getString(PREF_PREFIX_MOODS + id, "").split(","),
                prefs.getBoolean(PREF_PREFIX_TASTE + id, false),
                prefs.getFloat(PREF_PREFIX_ADVENTUROUS + id, -1),
                prefs.getString(PREF_PREFIX_SONG_TYPES + id, "").split(","),
                prefs.getFloat(PREF_PREFIX_SPEECHINESS + id, -1),
                prefs.getFloat(PREF_PREFIX_ENERGY + id, -1),
                prefs.getFloat(PREF_PREFIX_FAMILIAR + id, -1),
                id
        );
    }

    public List<AutoMixBucket> getBuckets() {
        return mBuckets;
    }

    public AutoMixBucket getCurrentPlayingBucket() {
        return mCurrentPlayingBucket;
    }

    public void startPlay(AutoMixBucket bucket) {
        IPlaybackService playback = PluginsLookup.getDefault().getPlaybackService();
        try {
            String trackRef = null;
            int tryCount = 0;
            while (trackRef == null && tryCount < 5) {
                trackRef = bucket.getNextTrack();
                tryCount++;
            }

            if (trackRef == null) {
                Log.e(TAG, "Track Reference is still null after 5 attempts");
                Utils.shortToast(mContext, R.string.bucket_track_failure);
            } else {
                Song s = ProviderAggregator.getDefault().getCache().getSong(trackRef);
                if (s == null) {
                    String prefix = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
                    if (prefix != null) {
                        ProviderIdentifier id = ProviderAggregator.getDefault().getRosettaStoneIdentifier(prefix);
                        s = ProviderAggregator.getDefault().retrieveSong(trackRef, id);
                    }
                }

                if (s != null) {
                    try {
                        playback.playSong(s);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot start playing bucket", e);
                    } finally {
                        mCurrentPlayingBucket = bucket;
                    }
                }
            }
        } catch (EchoNestException e) {
            Log.e(TAG, "Unable to get next track from bucket", e);
        }
    }


}