package com.vgram.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.camera.DownloadListener;
import dji.sdk.camera.FetchMediaTask;
import dji.sdk.camera.FetchMediaTaskContent;
import dji.sdk.camera.FetchMediaTaskScheduler;
import dji.sdk.camera.MediaFile;
import dji.sdk.camera.MediaManager;

/**
 * File Activity,
 * 드론 SD카드에서의 파일 다운로드 및 제거 관련 액티비티,
 * Image Activity 에서 인텐트 연결
 */

public class FileActivity extends Activity implements View.OnClickListener {

    private static final String TAG = FileActivity.class.getName();

    private Button mBackBtn, mDeleteBtn, mReloadBtn, mDownloadBtn;
    private RecyclerView listView;

    private FileListAdapter mListAdapter;
    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    private ProgressDialog mLoadingDialog;
    private ProgressDialog mDownloadDialog;
    private SlidingDrawer mPushDrawerSd;
    File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/VgramDemo/");
    private int currentProgress = -1;
    private ImageView mDisplayImageView;
    private int lastClickViewIndex = -1;
    private View lastClickView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //화면 회전 설정
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initMediaManager();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lastClickView = null;
        if (mMediaManager != null) {
            mMediaManager.stop(null);
            mMediaManager.removeFileListStateCallback(this.updateFileListStateListener);
            mMediaManager.exitMediaDownloading();
            if (scheduler != null) {
                scheduler.removeAllTasks();
            }
        }

        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        super.onDestroy();
    }

    void initUI() {

        //Init RecyclerView
        listView = (RecyclerView) findViewById(R.id.filelistView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(FileActivity.this, OrientationHelper.VERTICAL, false);
        listView.setLayoutManager(layoutManager);

        //Init FileListAdapter
        mListAdapter = new FileListAdapter();
        listView.setAdapter(mListAdapter);

        //Init Loading Dialog
        mLoadingDialog = new ProgressDialog(FileActivity.this);
        mLoadingDialog.setMessage("잠시만 기다려주세요..");
        mLoadingDialog.setCanceledOnTouchOutside(false);
        mLoadingDialog.setCancelable(false);

        //Init Download Dialog
        mDownloadDialog = new ProgressDialog(FileActivity.this);
        mDownloadDialog.setTitle("파일 다운로드");
        mDownloadDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDownloadDialog.setCanceledOnTouchOutside(false);
        mDownloadDialog.setCancelable(true);
        mDownloadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mMediaManager != null) {
                    mMediaManager.exitMediaDownloading();
                }
            }
        });

        mPushDrawerSd = (SlidingDrawer) findViewById(R.id.pointing_drawer_sd);
        mBackBtn = (Button) findViewById(R.id.back_btn);
        mDeleteBtn = (Button) findViewById(R.id.delete_btn);
        mDownloadBtn = (Button) findViewById(R.id.download_btn);
        mReloadBtn = (Button) findViewById(R.id.reload_btn);
        mDisplayImageView = (ImageView) findViewById(R.id.imageView);
        mDisplayImageView.setVisibility(View.VISIBLE);

        mBackBtn.setOnClickListener(this);
        mDeleteBtn.setOnClickListener(this);
        mDownloadBtn.setOnClickListener(this);
        mReloadBtn.setOnClickListener(this);
        mDownloadBtn.setOnClickListener(this);
    }

    /**
     * Dialog 관련 함수들
     */
    private void showProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (mLoadingDialog != null) {
                    mLoadingDialog.show();
                }
            }
        });
    }

    private void hideProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (null != mLoadingDialog && mLoadingDialog.isShowing()) {
                    mLoadingDialog.dismiss();
                }
            }
        });
    }

    private void ShowDownloadProgressDialog() {
        if (mDownloadDialog != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.incrementProgressBy(-mDownloadDialog.getProgress());
                    mDownloadDialog.show();
                }
            });
        }
    }

    private void HideDownloadProgressDialog() {
        if (null != mDownloadDialog && mDownloadDialog.isShowing()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.dismiss();
                }
            });
        }
    }

    //토스트 보이기
    private void setResultToToast(final String result) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(FileActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initMediaManager() {

        //연결된 프로덕트 정보 없음
        if (DemoApplication.getProductInstance() == null) {
            mediaFileList.clear();
            mListAdapter.notifyDataSetChanged();
            DJILog.e(TAG, "Product disconnected");
            return;

        } else {

            //카메라 인스턴스 접속 완료
            if (null != DemoApplication.getCameraInstance() && DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                mMediaManager = DemoApplication.getCameraInstance().getMediaManager();

                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    DemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, new CommonCallbacks.CompletionCallback() {

                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                DJILog.e(TAG, "카메라 모드 변경완료");
                                showProgressDialog();
                                getFileList();
                            } else {
                                setResultToToast("카메라 모드 변경실패");
                            }
                        }
                    });

                    scheduler = mMediaManager.getScheduler();
                }

                //카메라 인스턴스 접속 실패
            } else if (null != DemoApplication.getCameraInstance()
                    && !DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                setResultToToast("기기가 미디어 다운로드를 지원하지 않습니다.");
            }
        }
        return;
    }

    private void getFileList() {

        mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
        if (mMediaManager != null) {

            if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)) {
                DJILog.e(TAG, "Media Manager is busy.");
            } else {

                //SYNCING or DELETING 명령이 실행되지 않고있는 경우에 실행됨
                mMediaManager.refreshFileList(new CommonCallbacks.CompletionCallback() {

                    @Override
                    public void onResult(DJIError error) {
                        if (null == error) {
                            hideProgressDialog();

                            //Reset data
                            if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                                mediaFileList.clear();
                                lastClickViewIndex = -1;
                                lastClickView = null;
                            }

                            mediaFileList = mMediaManager.getFileListSnapshot();

                            //생성된 시간에 따라 소팅
                            Collections.sort(mediaFileList, new Comparator<MediaFile>() {
                                @Override
                                public int compare(MediaFile lhs, MediaFile rhs) {
                                    if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                        return 1;
                                    } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                        return -1;
                                    }
                                    return 0;
                                }
                            });

                            scheduler.resume(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {
                                    if (error == null) {
                                        getThumbnails();
                                        getPreviews();
                                    }
                                }
                            });

                            //onResult 메소드에서 에러 발생시
                        } else {
                            hideProgressDialog();
                            setResultToToast("파일 리스트 작성 실패:" + error.getDescription());
                        }
                    }
                });
            }
        }
    }

    private void getThumbnails() {
        if (mediaFileList.size() <= 0) {
            setResultToToast("섬네일을 지닌 파일이 없습니다.");
            return;
        }
        for (int i = 0; i < mediaFileList.size(); i++) {
            getThumbnailByIndex(i);
        }
    }

    private void getPreviews() {
        if (mediaFileList.size() <= 0) {
            setResultToToast("미리보기를 지닌 파일이 없습니다.");
            return;
        }
        for (int i = 0; i < mediaFileList.size(); i++) {
            getPreviewByIndex(i);
        }
    }

    private FetchMediaTask.Callback taskCallback = new FetchMediaTask.Callback() {

        @Override
        public void onUpdate(MediaFile file, FetchMediaTaskContent option, DJIError error) {

            if (null == error) {
                if (option == FetchMediaTaskContent.PREVIEW) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }
                if (option == FetchMediaTaskContent.THUMBNAIL) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }

                //에러 발생시
            } else {
                DJILog.e(TAG, "Fetch Media Task Failed" + error.getDescription());
            }

        }
    };

    //파일 인덱스에서 섬네일 로드
    private void getThumbnailByIndex(final int index) {
        FetchMediaTask task = new FetchMediaTask(mediaFileList.get(index), FetchMediaTaskContent.THUMBNAIL, taskCallback);
        scheduler.moveTaskToEnd(task);
    }

    //파일 인덱스에서 미리보기 로드
    private void getPreviewByIndex(final int index) {
        FetchMediaTask task = new FetchMediaTask(mediaFileList.get(index), FetchMediaTaskContent.PREVIEW, taskCallback);
        scheduler.moveTaskToEnd(task);
    }

    //사진정보 클래스, media_info_item 레이아웃과 연계
    private class ItemHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail_img;
        TextView file_name;
        TextView file_type;
        TextView file_size;
        TextView file_time;

        public ItemHolder(View itemView) {
            super(itemView);
            this.thumbnail_img = (ImageView) itemView.findViewById(R.id.filethumbnail);
            this.file_name = (TextView) itemView.findViewById(R.id.filename);
            this.file_type = (TextView) itemView.findViewById(R.id.filetype);
            this.file_size = (TextView) itemView.findViewById(R.id.fileSize);
            this.file_time = (TextView) itemView.findViewById(R.id.filetime);
        }
    }

    //FileListAdapter, 핵심 리스너
    private class FileListAdapter extends RecyclerView.Adapter<ItemHolder> {

        @Override
        public int getItemCount() {
            if (mediaFileList != null) {
                return mediaFileList.size();
            }
            return 0;
        }

        @Override
        public ItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_info_layout, parent, false);
            return new ItemHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemHolder mItemHolder, final int index) {

            final MediaFile mediaFile = mediaFileList.get(index);
            if (mediaFile != null) {
                if (mediaFile.getMediaType() != MediaFile.MediaType.MOV && mediaFile.getMediaType() != MediaFile.MediaType.MP4) {
                    mItemHolder.file_time.setVisibility(View.GONE);
                } else {
                    mItemHolder.file_time.setVisibility(View.VISIBLE);
                    mItemHolder.file_time.setText(mediaFile.getDurationInSeconds() + " s");
                }

                mItemHolder.file_name.setText(mediaFile.getFileName());
                mItemHolder.file_type.setText(mediaFile.getMediaType().name());
                mItemHolder.file_size.setText(mediaFile.getFileSize() + " Bytes");
                mItemHolder.thumbnail_img.setImageBitmap(mediaFile.getThumbnail());
                mItemHolder.thumbnail_img.setOnClickListener(ImgOnClickListener);
                mItemHolder.thumbnail_img.setTag(mediaFile);
                mItemHolder.itemView.setTag(index);

                if (lastClickViewIndex == index) {
                    mItemHolder.itemView.setSelected(true);
                } else {
                    mItemHolder.itemView.setSelected(false);
                }

                mItemHolder.itemView.setOnClickListener(itemViewOnClickListener);
            }
        }
    }

    /**
     * itemViewOnClickListener && ImgOnClickListener 의 두가지 리스너로 구성
     * 이미지 클릭시, 파일 정보 클릭시 작동
     */
    private View.OnClickListener itemViewOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            lastClickViewIndex = (int) (v.getTag());

            if (lastClickView != null && lastClickView != v) {
                lastClickView.setSelected(false);
            }
            v.setSelected(true);
            lastClickView = v;
        }
    };

    private View.OnClickListener ImgOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            MediaFile selectedMedia = (MediaFile )v.getTag();
            final Bitmap previewImage = selectedMedia.getPreview();
            runOnUiThread(new Runnable() {
                public void run() {
                    mDisplayImageView.setVisibility(View.VISIBLE);
                    mDisplayImageView.setImageBitmap(previewImage);
                }
            });
        }
    };

    private MediaManager.FileListStateListener updateFileListStateListener = new MediaManager.FileListStateListener() {
        @Override
        public void onFileListStateChange(MediaManager.FileListState state) {
            currentFileListState = state;
        }
    };

    //다운로드
    private void downloadFileByIndex(final int index){

        if(lastClickView == null) {
            setResultToToast("파일 지정 필요.");
            return;
        }

        if ((mediaFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA)
                || (mediaFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            return;
        }

        mediaFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                HideDownloadProgressDialog();
                setResultToToast("파일 다운로드 실패, " + error.getDescription());
                currentProgress = -1;
            }

            @Override
            public void onProgress(long total, long current) {
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    mDownloadDialog.setProgress(tmpProgress);
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onStart() {
                currentProgress = -1;
                ShowDownloadProgressDialog();
            }

            @Override
            public void onSuccess(String filePath) {
                HideDownloadProgressDialog();
                setResultToToast("파일 다운로드 완료, " + filePath);
                currentProgress = -1;
            }
        });

    }

    //삭제
    private void deleteFileByIndex(final int index) {

        if(lastClickView == null) {
            setResultToToast("파일 지정 필요.");
            return;
        }

        ArrayList<MediaFile> fileToDelete = new ArrayList<MediaFile>();

        if (mediaFileList.size() > index) {
            fileToDelete.add(mediaFileList.get(index));
            mMediaManager.deleteFiles(fileToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {

                @Override
                public void onSuccess(List<MediaFile> x, DJICameraError y) {
                    DJILog.e(TAG, "파일 삭제 완료.");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            MediaFile file = mediaFileList.remove(index);

                            //Reset select view
                            lastClickViewIndex = -1;
                            lastClickView = null;

                            //Update recyclerView
                            mListAdapter.notifyItemRemoved(index);
                        }
                    });
                }

                @Override
                public void onFailure(DJIError error) {
                    setResultToToast("파일 삭제 실패.");
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back_btn: {
                this.finish();
                break;
            }
            case R.id.delete_btn:{
                deleteFileByIndex(lastClickViewIndex);
                break;
            }
            case R.id.reload_btn: {
                getFileList();
                break;
            }
            case R.id.download_btn: {
                downloadFileByIndex(lastClickViewIndex);
                break;
            }
            default:
                break;
        }
    }

}
