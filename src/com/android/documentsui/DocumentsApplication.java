/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.OverlayManager;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.ClipStorage;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.queries.SearchHistoryManager;
import com.android.documentsui.roots.ProvidersCache;
import com.android.documentsui.theme.ThemeOverlayManager;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import java.util.List;

import javax.annotation.concurrent.GuardedBy;

public class DocumentsApplication extends Application {
    private static final String TAG = "DocumentsApplication";
    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;

    private static final List<String> PACKAGE_FILTER_ACTIONS = Lists.newArrayList(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_DATA_CLEARED
    );

    private static final List<String> PROFILE_FILTER_ACTIONS = Lists.newArrayList(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE
    );

    @GuardedBy("DocumentsApplication.class")
    @Nullable
    private static volatile ConfigStore sConfigStore;

    private ProvidersCache mProviders;
    private ThumbnailCache mThumbnailCache;
    private ClipStorage mClipStore;
    private DocumentClipper mClipper;
    private DragAndDropManager mDragAndDropManager;
    private UserIdManager mUserIdManager;
    private UserManagerState mUserManagerState;
    private Lookup<String, String> mFileTypeLookup;

    public static ProvidersCache getProvidersCache(Context context) {
        ProvidersCache providers =
                ((DocumentsApplication) context.getApplicationContext()).mProviders;
        final ConfigStore configStore = getConfigStore(context);
        // When private space in DocsUI is enabled then ProvidersCache should use UserManagerState
        // else it should use UserIdManager. The following if-check will ensure the construction of
        // a new ProvidersCache instance whenever there is a mismatch in this.
        if (configStore.isPrivateSpaceInDocsUIEnabled()
                != providers.isProvidersCacheUsingUserManagerState()) {
            providers = configStore.isPrivateSpaceInDocsUIEnabled()
                    ? new ProvidersCache(context, getUserManagerState(context), configStore)
                    : new ProvidersCache(context, getUserIdManager(context), configStore);
            ((DocumentsApplication) context.getApplicationContext()).mProviders = providers;
        }
        return providers;
    }

    public static ThumbnailCache getThumbnailCache(Context context) {
        final DocumentsApplication app = (DocumentsApplication) context.getApplicationContext();
        return app.mThumbnailCache;
    }

    public static ContentProviderClient acquireUnstableProviderOrThrow(
            ContentResolver resolver, String authority) throws RemoteException {
        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                authority);
        if (client == null) {
            throw new RemoteException("Failed to acquire provider for " + authority);
        }
        client.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        return client;
    }

    public static DocumentClipper getDocumentClipper(Context context) {
        return ((DocumentsApplication) context.getApplicationContext()).mClipper;
    }

    public static ClipStore getClipStore(Context context) {
        return ((DocumentsApplication) context.getApplicationContext()).mClipStore;
    }

    public static UserIdManager getUserIdManager(Context context) {
        UserIdManager userIdManager =
                ((DocumentsApplication) context.getApplicationContext()).mUserIdManager;
        if (userIdManager == null) {
            userIdManager = UserIdManager.create(context);
            ((DocumentsApplication) context.getApplicationContext()).mUserIdManager = userIdManager;
        }
        return userIdManager;
    }

    /**
     * UserManagerState class is used to maintain the list of userIds and other details like
     * cross profile access, label and badge associated with these userIds.
     */
    public static UserManagerState getUserManagerState(Context context) {
        UserManagerState userManagerState =
                ((DocumentsApplication) context.getApplicationContext()).mUserManagerState;
        if (userManagerState == null && getConfigStore(context).isPrivateSpaceInDocsUIEnabled()) {
            userManagerState = UserManagerState.create(context);
            ((DocumentsApplication) context.getApplicationContext()).mUserManagerState =
                    userManagerState;
        }
        return userManagerState;
    }

    public static DragAndDropManager getDragAndDropManager(Context context) {
        return ((DocumentsApplication) context.getApplicationContext()).mDragAndDropManager;
    }

    public static Lookup<String, String> getFileTypeLookup(Context context) {
        return ((DocumentsApplication) context.getApplicationContext()).mFileTypeLookup;
    }

    /**
     * Retrieve {@link ConfigStore} instance to access feature flags in production code.
     */
    public static synchronized ConfigStore getConfigStore(Context context) {
        if (sConfigStore == null) {
            sConfigStore = new ConfigStore.ConfigStoreImpl();
        }
        return sConfigStore;
    }

    /**
     * Set {@link #mProviders} as null onDestroy of BaseActivity so that new session uses new
     * instance of {@link #mProviders}
     */
    public static void invalidateProvidersCache(Context context) {
        ((DocumentsApplication) context.getApplicationContext()).mProviders = null;
    }

    /**
     * Set {@link #mUserManagerState} as null onDestroy of BaseActivity so that new session uses new
     * instance of {@link #mUserManagerState}
     */
    public static void invalidateUserManagerState(Context context) {
        ((DocumentsApplication) context.getApplicationContext()).mUserManagerState = null;
    }

    /**
     * Set {@link #sConfigStore} as null onDestroy of BaseActivity so that new session uses new
     * instance of {@link #sConfigStore}
     */
    public static void invalidateConfigStore() {
        synchronized (DocumentsApplication.class) {
            sConfigStore = null;
        }
    }

    private void onApplyOverlayFinish(boolean result) {
        Log.d(TAG, "OverlayManager.setEnabled() result: " + result);
    }

    @SuppressLint("NewApi") // OverlayManager.class is @hide
    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (DocumentsApplication.class) {
            if (sConfigStore == null) {
                sConfigStore = new ConfigStore.ConfigStoreImpl();
            }
        }

        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final OverlayManager om = getSystemService(OverlayManager.class);
        final int memoryClassBytes = am.getMemoryClass() * 1024 * 1024;

        if (om != null) {
            new ThemeOverlayManager(om, getPackageName()).applyOverlays(this, true,
                    this::onApplyOverlayFinish);
        } else {
            Log.w(TAG, "Can't obtain OverlayManager from System Service!");
        }

        if (getConfigStore(this).isPrivateSpaceInDocsUIEnabled()) {
            mUserManagerState = UserManagerState.create(this);
            mUserIdManager = null;
            synchronized (DocumentsApplication.class) {
                mProviders = new ProvidersCache(this, mUserManagerState, getConfigStore(this));
            }
        } else {
            mUserManagerState = null;
            mUserIdManager = UserIdManager.create(this);
            synchronized (DocumentsApplication.class) {
                mProviders = new ProvidersCache(this, mUserIdManager, getConfigStore(this));
            }
        }

        mProviders.updateAsync(/* forceRefreshAll= */ false, /* callback= */  null);

        mThumbnailCache = new ThumbnailCache(memoryClassBytes / 4);

        mClipStore = new ClipStorage(
                ClipStorage.prepareStorage(getCacheDir()),
                getSharedPreferences(ClipStorage.PREF_NAME, 0));
        mClipper = DocumentClipper.create(this, mClipStore);

        mDragAndDropManager = DragAndDropManager.create(this, mClipper);

        mFileTypeLookup = new FileTypeMap(this);

        final IntentFilter packageFilter = new IntentFilter();
        for (String packageAction : PACKAGE_FILTER_ACTIONS) {
            packageFilter.addAction(packageAction);
        }
        packageFilter.addDataScheme("package");
        registerReceiver(mCacheReceiver, packageFilter);

        final IntentFilter localeFilter = new IntentFilter();
        localeFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mCacheReceiver, localeFilter);

        if (SdkLevel.isAtLeastV()) {
            PROFILE_FILTER_ACTIONS.addAll(Lists.newArrayList(
                    Intent.ACTION_PROFILE_ADDED,
                    Intent.ACTION_PROFILE_REMOVED,
                    Intent.ACTION_PROFILE_AVAILABLE,
                    Intent.ACTION_PROFILE_UNAVAILABLE
            ));
        }
        final IntentFilter profileFilter = new IntentFilter();
        for (String profileAction : PROFILE_FILTER_ACTIONS) {
            profileFilter.addAction(profileAction);
        }
        registerReceiver(mCacheReceiver, profileFilter);

        SearchHistoryManager.getInstance(getApplicationContext());
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        mThumbnailCache.onTrimMemory(level);
    }

    private BroadcastReceiver mCacheReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Uri data = intent.getData();
            final String action = intent.getAction();
            if (PACKAGE_FILTER_ACTIONS.contains(action) && data != null) {
                final String packageName = data.getSchemeSpecificPart();
                mProviders.updatePackageAsync(UserId.DEFAULT_USER, packageName);
            } else if (PROFILE_FILTER_ACTIONS.contains(action)) {
                // After we have reloaded roots. Resend the broadcast locally so the other
                // components can reload properly after roots are updated.
                if (getConfigStore(context).isPrivateSpaceInDocsUIEnabled()) {
                    UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
                    UserId userId = UserId.of(userHandle);
                    getUserManagerState(context).onProfileActionStatusChange(action, userId);
                }
                mProviders.updateAsync(/* forceRefreshAll= */ true,
                        () -> LocalBroadcastManager.getInstance(context).sendBroadcast(intent));
            } else {
                mProviders.updateAsync(/* forceRefreshAll= */ true, /* callback= */ null);
            }
        }
    };
}
