#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <opencv2/tracking.hpp>

#define CSL CC_STAT_LEFT
#define CST CC_STAT_TOP
#define CSW CC_STAT_WIDTH
#define CSH CC_STAT_HEIGHT
#define SKIP 10
#define PI 3.141592

using namespace cv;
using namespace std;
using cv::ConnectedComponentsTypes;

Mat DetectRoad(Mat);

inline Point GetRectCenter(int, int, int, int);

MultiTracker deleteTrackingObject(Mat , int);

void JudgeTrackingDeletion(Mat );

bool CheckObject(int, int, Point);

bool JudgeRoadObject(Rect );

void LabelingObject(Mat, Mat);

void GroupCoordinate(Mat, vector<Point>);

void DetectObject(CascadeClassifier, Mat, Mat);

void SetMovingPoint(Mat, Mat, Mat, double);

void UpdateTracking(Mat);

void AddTrackingTarget(Rect _roi, Mat _mRgb);

Mat prev_gray, prev_stats, road_img;
int prev_nLab;
double prev_x;
vector<Point> left_point;
vector<Point> right_point;
CascadeClassifier car_cascade, human_cascade;
MultiTracker trackers;
vector<Ptr<Tracker> > algorithms;
vector<Rect2d> ROIs;
bool flag = false;
bool denger_flag;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_owner_myapplication_MainActivity_OpticalFlow
        (JNIEnv *env, jobject thiz, jstring jcarFileName, jstring jhumanFileName, jlong addrRgba, jdouble _x) {
    Mat &mRgb = *(Mat *) addrRgba;
    Mat current_gray;

    denger_flag = false;

    flip(mRgb, mRgb, 1);
    cvtColor(mRgb, mRgb, CV_RGBA2RGB);
    cvtColor(mRgb, current_gray, CV_RGB2GRAY);
    equalizeHist(current_gray, current_gray);
    double current_x = _x;
    double diff_x = current_x - prev_x;

    if (prev_gray.empty() == true) {
        const char *jnamestr_car = env->GetStringUTFChars(jcarFileName, NULL);
        string carFileName(jnamestr_car);
        const char *jnamestr_human = env->GetStringUTFChars(jhumanFileName, NULL);
        string humanFileName(jnamestr_human);
        car_cascade.load(carFileName);
        human_cascade.load(humanFileName);
        current_gray.copyTo(prev_gray);
        prev_x = current_x;
    } else {
        Mat current_down_frame = Mat(current_gray, Rect(0, 220, mRgb.cols, 100));
        road_img = DetectRoad(current_down_frame);

        DetectObject(car_cascade, mRgb, current_gray);
        DetectObject(human_cascade, mRgb, current_gray);

        if (flag) {
            UpdateTracking(mRgb);
            JudgeTrackingDeletion(mRgb);
        }

        //SetMovingPoint(mRgb, current_gray, prev_gray, diff_x);
        //Mat Dst = Mat::zeros(mRgb.rows, mRgb.cols, CV_8UC1);

//        if (diff_x < 0) {
//            GroupCoordinate(Dst, left_point);
//        } else if (diff_x > 0) {
//            GroupCoordinate(Dst, right_point);
//        }

//        LabelingObject(Dst, mRgb);
        current_gray.copyTo(prev_gray);
        prev_x = current_x;
    }

    if (denger_flag) {
        return true;
    } else {
        return false;
    }
}

/**Tracker検出を判断**/
bool JudgeDetection(unsigned i, Mat frame) {
    bool Result = true;
    /*検出対象の横幅が超過*/
    if (trackers.getObjects()[i].x + trackers.getObjects()[i].width > frame.cols) {
        Result = false;
    }
    if (trackers.getObjects()[i].x < 0) {
        Result = false;
    }
    /*検出対象の縦幅が超過*/
    if (trackers.getObjects()[i].y + trackers.getObjects()[i].height > frame.rows) {
        Result = false;
    }
    if (trackers.getObjects()[i].y < 90) {
        Result = false;
    }
    return Result;
}

void JudgeTrackingDeletion(Mat _frame) {
    for (int i = 0; i < trackers.getObjects().size(); i++) {
        if (!JudgeRoadObject(trackers.getObjects()[i]) || !JudgeDetection(i, _frame)) {
            cout << "削除済み" << endl;
            trackers = deleteTrackingObject(_frame, i);
            return;
        }
    }
}

MultiTracker deleteTrackingObject(Mat _frame, int number) {
    MultiTracker tracker;
    ROIs.erase(ROIs.begin() + number);
    algorithms.erase(algorithms.begin() + number);
    tracker.add(algorithms, _frame, ROIs);

    return tracker;
}

/**
 * @fn 道路上の物体か判断する
 * @return 道路上ならtrue それ以外ならfalse*/
bool JudgeRoadObject(Rect _object) {
    int y = (_object.y + _object.height) * road_img.step;
    unsigned char r;
    for (int x = _object.x; x < _object.width + _object.x; x++) {
        r = road_img.data[y + x * 3];
        if (r == 255) { return true; }
    }
    return false;
}

/**
 * @fn cascadeファイルを用いて現在の画像から物体を検出する */
void DetectObject(CascadeClassifier cascade, Mat _mRgb, Mat _current_gray) {
    vector<Rect> objects;
    cascade.detectMultiScale(_current_gray, objects);

    for (int i = 0; i < objects.size(); i++) {
        if (JudgeRoadObject(objects[i])) {
            Point center = GetRectCenter(objects[i].x, objects[i].y, objects[i].width, objects[i].height);
            if (CheckObject(objects[i].width, objects[i].height, center)) {
                objects[i] = Rect(objects[i].x + 8, objects[i].y + 8, objects[i].width - 15, objects[i].height - 15);
                AddTrackingTarget(objects[i], _mRgb);
            }
        }
    }
}

/**
 * @fn Trackerに追跡対象を追加する*/
void AddTrackingTarget(Rect _roi, Mat _mRgb) {
    algorithms.push_back(TrackerMedianFlow::create());
    ROIs.push_back(_roi);
    trackers.add(algorithms[algorithms.size() - 1], _mRgb, ROIs[ROIs.size() - 1]);
    flag = true;
}

/**
 * @fn オプティカルフローから自車量の動きに反しているベクトルを抽出し、その座標を保存
 * @param _diff_x 前回の傾きと現在の傾きを引いた値 */
void SetMovingPoint(Mat _mRgb, Mat _current_gray, Mat _prev_gray, double _diff_x) {
    Mat flow;
    UMat flowUmat;
    calcOpticalFlowFarneback(_prev_gray, _current_gray, flowUmat, 0.4, 1, 12, 2, 8, 1.2, 0);
    flowUmat.copyTo(flow);

    double angle, length;
    cvtColor(_mRgb, _mRgb, CV_RGB2HSV);
    for (int y = 120; y < _mRgb.rows; y += SKIP) {
        for (int x = 0; x < _mRgb.cols; x += SKIP) {
            Point2f flowxy = flow.at<Point2f>(y, x);
            angle = atan2(flowxy.y, flowxy.x);
            angle = (angle + PI) / 2 / PI * 180;
            length = pow(pow(flowxy.x, 2) + pow(flowxy.y, 2), 0.5);
            if ((158 < angle || angle < 23) && _diff_x < 0 && length > 1 && x > _mRgb.cols / 2) {
                line(_mRgb, Point2f(x, y), Point2f(cvRound(x + flowxy.x), cvRound(y + flowxy.y)), Scalar(angle, 255, 255), 2);
                left_point.push_back(Point(x, y));
            }
            if ((68 < angle && angle < 113) && _diff_x > 0 && length > 1 && x < _mRgb.cols / 2) {
                line(_mRgb, Point2f(x, y), Point2f(cvRound(x + flowxy.x), cvRound(y + flowxy.y)), Scalar(angle, 255, 255), 2);
                right_point.push_back(Point(x, y));
            }
        }
    }
    cvtColor(_mRgb, _mRgb, CV_HSV2RGB);
}


inline bool judge_hit(int _current_z, int center_x) {
    if (_current_z < 400 && 100 < center_x && center_x < 150) {
        denger_flag = true;
        return true;
    }
    return false;
}

/**
 * @fn 追跡を更新して描画する */
void UpdateTracking(Mat _mRgb) {
    trackers.update(_mRgb);
    for (unsigned i = 0; i < trackers.getObjects().size(); i++) {
//        ostringstream os;
//        os << floor(trackers.getObjects()[i].area());
//        putText(_mRgb, os.str(), Point(trackers.getObjects()[i].x, trackers.getObjects()[i].y - 5), FONT_HERSHEY_SIMPLEX, 0.5, Scalar(0, 0, 200), 1, CV_AA);
        int current_center_width = trackers.getObjects()[i].x + trackers.getObjects()[i].width / 2;
        line(_mRgb, Point(current_center_width, trackers.getObjects()[i].y + trackers.getObjects()[i].height), Point(current_center_width, _mRgb.rows), Scalar(0, 255, 0), 1, CV_AA);
        int current_z = _mRgb.rows - (trackers.getObjects()[i].y + trackers.getObjects()[i].height);
        current_z *= 17.1265;
        ostringstream oss;
        oss << current_z;
        putText(_mRgb, oss.str(), Point(trackers.getObjects()[i].x, trackers.getObjects()[i].y - 5), FONT_HERSHEY_SIMPLEX,1,Scalar(255,0,0));
        if (judge_hit(current_z, current_center_width)) {
            rectangle(_mRgb, trackers.getObjects()[i], Scalar(255, 0, 0), 2, 1);
        }
        else {
            rectangle(_mRgb, trackers.getObjects()[i], Scalar(0, 0, 255), 2, 1);
        }
    }
}

/**
 * @fn 道路領域の抽出 */
Mat DetectRoad(Mat _current_down_frame) {
    Mat bin_img = Mat::zeros(320, 240, CV_8UC1);

    vector<Point2f> currentCorners;

    goodFeaturesToTrack(_current_down_frame, currentCorners, 50, 0.01, 5.0);

    cornerSubPix(_current_down_frame, currentCorners, Size(21, 21), Size(-1, 1),
                 TermCriteria(TermCriteria::COUNT | TermCriteria::EPS, 40, 0.01));

    vector<Point> Corners;
    for (int x = 0; x < _current_down_frame.cols; x++) {
        int max_x = 0, max_y = 0;
        bool add_flag = false;
        for (int i = 0; i < currentCorners.size(); i++) {
            Point p1 = Point((int) currentCorners[i].x, (int) currentCorners[i].y);
            if (x == p1.x && max_y < p1.y) {
                max_x = p1.x;
                max_y = p1.y;
                add_flag = true;
            }
//            circle(_current_frame, Point(p1.x, p1.y), 1, Scalar(255, 0, 0), 1, 4);
        }
        if (add_flag) {
            Corners.push_back(Point(max_x, max_y));
        }
    }

    line(bin_img, Point(0, (Corners[0].y + 220)), Point(Corners[0].x, (Corners[0].y + 220)), Scalar(255, 0, 0), 2);
    for (int i = 0; i < Corners.size(); i++) {
        if (i != Corners.size() - 1) {
            line(bin_img, Point(Corners[i].x, (Corners[i].y + 220)), Point(Corners[i + 1].x, (Corners[i + 1].y + 220)), Scalar(255, 0, 0), 2);
        }
    }
    line(bin_img, Point(_current_down_frame.cols, (Corners[Corners.size() - 1].y + 220)), Point(Corners[Corners.size() - 1].x, (Corners[Corners.size() - 1].y + 220)), Scalar(255, 0, 0), 2);

    bin_img = ~bin_img;

    Mat LabelImg;
    Mat stats;
    Mat centroids;
    int nLab = connectedComponentsWithStats(bin_img, LabelImg, stats, centroids);

    // ラベリング結果の描画色を決定
    vector<Vec3b> colors(nLab);
    colors[0] = Vec3b(0, 0, 0);
    colors[1] = Vec3b(0, 0, 0);
    colors[2] = Vec3b(255, 0, 0);

    Mat Dst = Mat::zeros(320, 240, CV_8UC3);
    // ラベリング結果の描画
    for (int i = 220; i < bin_img.rows; ++i) {
        int *lb = LabelImg.ptr<int>(i);
        Vec3b *pix = Dst.ptr<Vec3b>(i);
        for (int j = 0; j < bin_img.cols; ++j) {
            pix[j] = colors[lb[j]];
        }
    }

    return Dst;
}

/**
 * @fn 格納されている座標をグループ化して白黒画像を作成する*/
void GroupCoordinate(Mat _frame, vector<Point> list) {
    double distance;
    Point p;
    for (int i = 0; i < list.size(); i++) {
        for (int j = i + 1; j < list.size(); j++) {
            distance = sqrt(pow(list[j].x - list[i].x, 2) + pow(list[j].y - list[i].y, 2));
            if (distance == SKIP) {
                p = Point(list[j].x - list[i].x, list[j].y - list[i].y);

                if (p.x == 0) {
                    rectangle(_frame, Point2f(list[i].x, list[i].y), Point2f(list[i].x + SKIP, list[i].y + p.y), Scalar(255, 255, 255), -1);
                } else {
                    rectangle(_frame, Point2f(list[i].x, list[i].y), Point2f(list[i].x + p.x, list[i].y + SKIP), Scalar(255, 255, 255), -1);
                }
            }
        }
    }
    left_point.clear();
    right_point.clear();
}

/**
 * @@fn 矩形の中心座標を求める */
inline Point GetRectCenter(int _x, int _y, int _width, int _height) {
    return Point(_x + _width / 2, _y + _height / 2);
}

/**
 * @fn ラベリングのパラメータから矩形の中心座標を返す
 * @param _param 矩形のx,y,width,heightの値を渡す */
inline Point GetRectCenterFromParam(int *_param) {
    return GetRectCenter(_param[CSL], _param[CST], _param[CSW], _param[CSH]);
}

/**
 * @fn 矩形同士のあたり判定をする
 * @return 当たっているならtrue それ以外ならfalse */
inline bool JudgeHitRect(Point _diff_length, int _hit_width, int _hit_height) {
    if (_diff_length.x < _hit_width && _diff_length.y < _hit_height)
        return true;
    else
        return false;
}

/**
 * @fn すでに追跡中の物体でないかチェックする
 * @return 追跡中の物体ならfalse 追跡中でないのならtrueを返す*/
bool CheckObject(int _current_width, int _current_height, Point _current_center) {
    Rect tracker_param;
    Point tracker_center, tracker_length;
    int tracker_width, tracker_height;
    for (int i = 0; i < trackers.getObjects().size(); i++) {
        tracker_param = trackers.getObjects()[i];
        tracker_center = GetRectCenter(tracker_param.x, tracker_param.y, tracker_param.width, tracker_param.height);
        tracker_length = Point(abs(_current_center.x - tracker_center.x), abs(_current_center.y - tracker_center.y));
        tracker_width = (_current_width + trackers.getObjects()[i].width) / 2;
        tracker_height = (_current_height + trackers.getObjects()[i].height) / 2;

        if (tracker_length.x < tracker_width && tracker_length.y < tracker_height) {
            return false;
        }
    }
    return true;
}

/**
 * @fn 座標をグループ化した画像から領域を分けて追跡対象に追加するか判断する*/
void LabelingObject(Mat _frame, Mat _mRgb) {
    Mat LabelImg, stats, centroids;
    int nLab = connectedComponentsWithStats(_frame, LabelImg, stats, centroids);

    //ROIの設定
    if (prev_stats.empty() == true) {
        prev_stats = stats.clone();
        prev_nLab = nLab;
    } else {
        for (int i = 1; i < nLab; i++) {
            int *current_param = stats.ptr<int>(i);
            Rect roi = Rect(current_param[CSL], current_param[CST], current_param[CSW], current_param[CSH]);
            if (JudgeRoadObject(roi)) {
                if (current_param[CC_STAT_AREA] > 500) {
                    Point current_center = GetRectCenterFromParam(current_param);
                    for (int j = 1; j < prev_nLab; j++) {
                        int *prev_param = prev_stats.ptr<int>(j);
                        if (prev_param[CC_STAT_AREA] > 500) {
                            Point prev_center = GetRectCenterFromParam(prev_param);
                            Point diff_length = Point(abs(current_center.x - prev_center.x), abs(current_center.y - prev_center.y));
                            int hit_width = (current_param[CSW] + prev_param[CSW]) / 2;
                            int hit_height = (current_param[CSH] + prev_param[CSH]) / 2;

                            if (JudgeHitRect(diff_length, hit_width, hit_height) &&
                                CheckObject(current_param[CSW], current_param[CSH], current_center)) {
                                AddTrackingTarget(roi, _mRgb);
                            }
                        }
                    }
                }
            }
        }
        prev_stats = stats.clone();
        prev_nLab = nLab;
    }
}

