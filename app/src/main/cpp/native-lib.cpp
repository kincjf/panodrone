#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include <iostream>
#include <fstream>
#include <vector>
#include <android/log.h>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/stitching.hpp>

using namespace std;
using namespace cv;

#define  LOG_TAG    "VgramDemojni"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define  cutBlackThreshold   0.001
jclass javaClassRef;
jmethodID javaMethodRef;

extern "C"
JNIEXPORT jint JNICALL
Java_com_vgram_demo_ImageActivity_Stitch(JNIEnv *env, jobject obj, jobjectArray source, jstring result, jdouble scale);

//check row
bool checkRow(const cv::Mat& roi, int y) {
    int zeroCount = 0;
    for(int x=0; x<roi.cols; x++) {
        if(roi.at<uchar>(y, x) == 0) {
            zeroCount++;
        }
    }
    if((zeroCount/(float)roi.cols)>cutBlackThreshold) {
        return false;
    }
    return true;
}

//check col
bool checkColumn(const cv::Mat& roi, int x) {
    int zeroCount = 0;
    for(int y=0; y<roi.rows; y++) {
        if(roi.at<uchar>(y, x) == 0) {
            zeroCount++;
        }
    }
    if((zeroCount/(float)roi.rows)>cutBlackThreshold) {
        return false;
    }
    return true;
}

//find largest roi
bool cropLargestPossibleROI(const cv::Mat& gray, cv::Mat& pano, cv::Rect startROI)
{
    // evaluate start-ROI
    Mat possibleROI = gray(startROI);
    bool topOk = checkRow(possibleROI, 0);
    bool leftOk = checkColumn(possibleROI, 0);
    bool bottomOk = checkRow(possibleROI, possibleROI.rows-1);
    bool rightOk = checkColumn(possibleROI, possibleROI.cols-1);
    if(topOk && leftOk && bottomOk && rightOk) {
        LOGE("cropLargestPossibleROI success");
        pano = pano(startROI);
        return true;
    }
    // If not, scale ROI down
    Rect newROI(startROI.x, startROI.y, startROI.width, startROI.height);
    // if x is increased, width has to be decreased to compensate
    if(!leftOk) {
        newROI.x++;
        newROI.width--;
    }
    // same is valid for y
    if(!topOk) {
        newROI.y++;
        newROI.height--;
    }
    if(!rightOk) {
        newROI.width--;
    }
    if(!bottomOk) {
        newROI.height--;
    }
    if(newROI.x + startROI.width < 0 || newROI.y + newROI.height < 0) {
        LOGE("cropLargestPossibleROI failed");
        return false;
    }

    return cropLargestPossibleROI(gray,pano,newROI);
}

JNIEXPORT jint JNICALL
Java_com_vgram_demo_ImageActivity_Stitch(JNIEnv *env, jobject obj, jobjectArray source, jstring result, jdouble scale) {

    //init jni call java method
    static int once = 1;
    if(once) {
        jclass thisClass = env->GetObjectClass(obj);
        javaClassRef = (jclass) env->NewGlobalRef(thisClass);
        javaMethodRef = env->GetMethodID(javaClassRef, "stitchingCostSet", "(D)V");
        once = 0;
    }

    //init timer
    clock_t beginTime, endTime;
    double timeSpent;
    beginTime = clock();

    bool try_use_gpu = false;

    //input imgs
    vector<Mat> imgs;
    Mat img;
    Mat img_scaled;

    //output imgs
    Mat pano;
    Mat pano_tocut;
    Mat gray;

    //convert to jni
    const char* result_name = env->GetStringUTFChars(result, JNI_FALSE);
    LOGE("result_name=%s",result_name);
    LOGE("scale=%f",scale);

    int imgCount = env->GetArrayLength(source);
    LOGE("source imgCount=%d",imgCount);

    for(int i = 0; i < imgCount; i++) {
        jstring jsource = (jstring)(env->GetObjectArrayElement(source, i));
        const char* source_name = env->GetStringUTFChars(jsource, JNI_FALSE);
        LOGE("Add index %d source_name=:%s", i, source_name);

        img = imread(source_name);

        Size dsize = Size((int)(img.cols*scale), (int)(img.rows*scale));
        img_scaled = Mat(dsize, CV_32S);
        resize(img, img_scaled, dsize);

        imgs.push_back(img_scaled);

        env->ReleaseStringUTFChars(jsource, source_name);
    }
    img.release();

    Stitcher stitcher = Stitcher::createDefault(try_use_gpu);
    Stitcher::Status status = stitcher.stitch(imgs, pano);

    //error occurs
    if(status != Stitcher::OK)
        return (int)status;

    for(int i = 0; i < imgs.size(); i++) {
        imgs[i].release();
    }

    pano_tocut = pano;
    cvtColor(pano_tocut, gray, CV_BGR2GRAY);
    Rect startROI(0, 0, gray.cols, gray.rows); // start as the source image - ROI is the complete SRC-Image
    cropLargestPossibleROI(gray, pano_tocut, startROI);
    gray.release();

    //save pano
    imwrite(result_name, pano_tocut);

    pano.release();
    pano_tocut.release();
    env->ReleaseStringUTFChars(result, result_name);

    endTime = clock();
    timeSpent = (double)(endTime - beginTime) / CLOCKS_PER_SEC;

    env->CallVoidMethod(obj, javaMethodRef, timeSpent);
    LOGE("success,total cost time %f seconds",timeSpent);

    return 0;
}