package com.edisonwang.android.ipes;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.edisonwang.eventservice.annotations.EventProducer;
import com.edisonwang.eventservice.annotations.ParcelableClassField;
import com.edisonwang.eventservice.annotations.RequestFactory;
import com.edisonwang.eventservice.annotations.ClassField;
import com.edisonwang.eventservice.annotations.RequestFactoryWithVariables;
import com.edisonwang.eventservice.annotations.ResultClassWithVariables;
import com.edisonwang.eventservice.baggers.ParcelableBagger;
import com.edisonwang.eventservice.lib.Action;
import com.edisonwang.eventservice.lib.ActionKey;
import com.edisonwang.eventservice.lib.ActionKey_.Samples;
import com.edisonwang.eventservice.lib.ActionRequest;
import com.edisonwang.eventservice.lib.ActionRequestBuilder;
import com.edisonwang.eventservice.lib.ActionResult;
import com.edisonwang.eventservice.lib.EventServiceImpl;

import java.util.Random;

/**
 * @author edi
 */
@EventProducer(events = {
        ComplicatedAction.SampleActionSuccessEvent.class,
        ComplicatedAction.SampleActionFailedEvent.class
}, generated = {
        @ResultClassWithVariables(classPostFix = "Sample", baseClass = ActionResult.class,
                fields = {
                        @ParcelableClassField(
                                name = "sampleParam3",
                                kind = String.class,
                                bagger = SampleStringParceler.class),
                        @ParcelableClassField(
                                name = "sampleParcelable",
                                kind = ComplicatedAction.SampleParcelable.class,
                                bagger = ParcelableBagger.class)
                }),
})
@RequestFactory(
        baseClass = ActionKey.class,
        valueType = Action.class,
        group = "Samples"
)
@RequestFactoryWithVariables(baseClass = ActionRequestBuilder.class, variables = {
        @ClassField(name = "sampleParam", kind = String.class),
        @ClassField(name = "sampleParamTwo", kind = ComplicatedAction.SampleParcelable.class),
        @ClassField(name = "shouldFail", kind = Boolean.class)
}
)
public class ComplicatedAction implements Action {

    private static final String TAG = "ComplicatedAction";

    private static final Random sRandom = new Random();

    @Override
    public ActionResult processRequest(EventServiceImpl service, ActionRequest actionRequest) {
        ComplicatedActionHelper helper = Samples.complicatedAction();
        helper.setVariableValues(actionRequest.getArguments(getCurrentClassLoader()));
        Log.i(TAG, "Processing requestAction " + helper.sampleParamTwo().mTestName);
        if (helper.shouldFail()) {
            return new SampleActionFailedEvent(helper.sampleParam(), helper.sampleParamTwo());
        } else {
            if (sRandom.nextInt() % 2 == 0) {
                return new SampleActionSuccessEvent(helper.sampleParam(), helper.sampleParamTwo());
            } else {
                ComplicatedActionEventSample generatedEvent = new ComplicatedActionEventSample();
                generatedEvent.sampleParam3 = "sampleParam3";
                return generatedEvent;
            }
        }
    }

    /**
     * @return the class loader associated with the current module,
     * you may want to use a different class loader.
     */
    private ClassLoader getCurrentClassLoader() {
        return getClass().getClassLoader();
    }

    @SuppressLint("ParcelCreator")
    public static abstract class SampleActionEvent extends ActionResult {

        public SampleParcelable mSampleParcelable;
        public String mSampleParam;

        public SampleActionEvent(String sampleParam, SampleParcelable parcelable) {
            mSampleParam = sampleParam;
            mSampleParcelable = parcelable;
        }

        public SampleActionEvent(Parcel in) {
            super(in);
            mSampleParam = in.readString();
            mSampleParcelable = in.readParcelable(SampleParcelable.class.getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSampleParam);
            dest.writeParcelable(mSampleParcelable, flags);
        }
    }

    public static class SampleActionSuccessEvent extends SampleActionEvent {

        public SampleActionSuccessEvent(String s, SampleParcelable parcelable) {
            super(s, parcelable);
        }

        public SampleActionSuccessEvent(Parcel in) {
            super(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<SampleActionSuccessEvent> CREATOR
                = new Parcelable.Creator<SampleActionSuccessEvent>() {
            public SampleActionSuccessEvent createFromParcel(Parcel in) {
                return new SampleActionSuccessEvent(in);
            }

            public SampleActionSuccessEvent[] newArray(int size) {
                return new SampleActionSuccessEvent[size];
            }
        };
    }

    public static class SampleActionFailedEvent extends SampleActionEvent {

        public SampleActionFailedEvent(String s, SampleParcelable parcelable) {
            super(s, parcelable);
        }

        public SampleActionFailedEvent(Parcel in) {
            super(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<SampleActionFailedEvent> CREATOR
                = new Parcelable.Creator<SampleActionFailedEvent>() {
            public SampleActionFailedEvent createFromParcel(Parcel in) {
                return new SampleActionFailedEvent(in);
            }

            public SampleActionFailedEvent[] newArray(int size) {
                return new SampleActionFailedEvent[size];
            }
        };
    }

    public static class SampleParcelable implements Parcelable {

        public String mTestName;

        public SampleParcelable(String testName) {
            mTestName = testName;
        }

        protected SampleParcelable(Parcel in) {
            mTestName = in.readString();
        }

        public static final Creator<SampleParcelable> CREATOR = new Creator<SampleParcelable>() {
            @Override
            public SampleParcelable createFromParcel(Parcel in) {
                return new SampleParcelable(in);
            }

            @Override
            public SampleParcelable[] newArray(int size) {
                return new SampleParcelable[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mTestName);
        }
    }
}