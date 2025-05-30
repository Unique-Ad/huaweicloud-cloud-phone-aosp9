/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef _UI_INPUT_READER_H
#define _UI_INPUT_READER_H

#include "EventHub.h"
#include "PointerControllerInterface.h"
#include "InputListener.h"

#include <input/DisplayViewport.h>
#include <input/Input.h>
#include <input/VelocityControl.h>
#include <input/VelocityTracker.h>
#include <ui/DisplayInfo.h>
#include <utils/KeyedVector.h>
#include <utils/Condition.h>
#include <utils/Thread.h>
#include <utils/Mutex.h>
#include <utils/Timers.h>
#include <utils/RefBase.h>
#include <utils/BitSet.h>
#include <utils/SortedVector.h>

#include <stddef.h>
#include <unistd.h>

// Maximum supported size of a vibration pattern.
// Must be at least 2.
#define MAX_VIBRATE_PATTERN_SIZE 100

// Maximum allowable delay value in a vibration pattern before
// which the delay will be truncated.
#define MAX_VIBRATE_PATTERN_DELAY_NSECS (1000000 * 1000000000LL)

namespace android {

class InputDevice;
class InputMapper;

/*
 * Input reader configuration.
 *
 * Specifies various options that modify the behavior of the input reader.
 */
struct InputReaderConfiguration {
    // Describes changes that have occurred.
    enum {
        // The pointer speed changed.
        CHANGE_POINTER_SPEED = 1 << 0,

        // The pointer gesture control changed.
        CHANGE_POINTER_GESTURE_ENABLEMENT = 1 << 1,

        // The display size or orientation changed.
        CHANGE_DISPLAY_INFO = 1 << 2,

        // The visible touches option changed.
        CHANGE_SHOW_TOUCHES = 1 << 3,

        // The keyboard layouts must be reloaded.
        CHANGE_KEYBOARD_LAYOUTS = 1 << 4,

        // The device name alias supplied by the may have changed for some devices.
        CHANGE_DEVICE_ALIAS = 1 << 5,

        // The location calibration matrix changed.
        CHANGE_TOUCH_AFFINE_TRANSFORMATION = 1 << 6,

        // The presence of an external stylus has changed.
        CHANGE_EXTERNAL_STYLUS_PRESENCE = 1 << 7,

        // The pointer capture mode has changed.
        CHANGE_POINTER_CAPTURE = 1 << 8,

        // The set of disabled input devices (disabledDevices) has changed.
        CHANGE_ENABLED_STATE = 1 << 9,

        // All devices must be reopened.
        CHANGE_MUST_REOPEN = 1 << 31,
    };

    // Gets the amount of time to disable virtual keys after the screen is touched
    // in order to filter out accidental virtual key presses due to swiping gestures
    // or taps near the edge of the display.  May be 0 to disable the feature.
    nsecs_t virtualKeyQuietTime;

    // The excluded device names for the platform.
    // Devices with these names will be ignored.
    Vector<String8> excludedDeviceNames;

    // Velocity control parameters for mouse pointer movements.
    VelocityControlParameters pointerVelocityControlParameters;

    // Velocity control parameters for mouse wheel movements.
    VelocityControlParameters wheelVelocityControlParameters;

    // True if pointer gestures are enabled.
    bool pointerGesturesEnabled;

    // Quiet time between certain pointer gesture transitions.
    // Time to allow for all fingers or buttons to settle into a stable state before
    // starting a new gesture.
    nsecs_t pointerGestureQuietInterval;

    // The minimum speed that a pointer must travel for us to consider switching the active
    // touch pointer to it during a drag.  This threshold is set to avoid switching due
    // to noise from a finger resting on the touch pad (perhaps just pressing it down).
    float pointerGestureDragMinSwitchSpeed; // in pixels per second

    // Tap gesture delay time.
    // The time between down and up must be less than this to be considered a tap.
    nsecs_t pointerGestureTapInterval;

    // Tap drag gesture delay time.
    // The time between the previous tap's up and the next down must be less than
    // this to be considered a drag.  Otherwise, the previous tap is finished and a
    // new tap begins.
    //
    // Note that the previous tap will be held down for this entire duration so this
    // interval must be shorter than the long press timeout.
    nsecs_t pointerGestureTapDragInterval;

    // The distance in pixels that the pointer is allowed to move from initial down
    // to up and still be called a tap.
    float pointerGestureTapSlop; // in pixels

    // Time after the first touch points go down to settle on an initial centroid.
    // This is intended to be enough time to handle cases where the user puts down two
    // fingers at almost but not quite exactly the same time.
    nsecs_t pointerGestureMultitouchSettleInterval;

    // The transition from PRESS to SWIPE or FREEFORM gesture mode is made when
    // at least two pointers have moved at least this far from their starting place.
    float pointerGestureMultitouchMinDistance; // in pixels

    // The transition from PRESS to SWIPE gesture mode can only occur when the
    // cosine of the angle between the two vectors is greater than or equal to than this value
    // which indicates that the vectors are oriented in the same direction.
    // When the vectors are oriented in the exactly same direction, the cosine is 1.0.
    // (In exactly opposite directions, the cosine is -1.0.)
    float pointerGestureSwipeTransitionAngleCosine;

    // The transition from PRESS to SWIPE gesture mode can only occur when the
    // fingers are no more than this far apart relative to the diagonal size of
    // the touch pad.  For example, a ratio of 0.5 means that the fingers must be
    // no more than half the diagonal size of the touch pad apart.
    float pointerGestureSwipeMaxWidthRatio;

    // The gesture movement speed factor relative to the size of the display.
    // Movement speed applies when the fingers are moving in the same direction.
    // Without acceleration, a full swipe of the touch pad diagonal in movement mode
    // will cover this portion of the display diagonal.
    float pointerGestureMovementSpeedRatio;

    // The gesture zoom speed factor relative to the size of the display.
    // Zoom speed applies when the fingers are mostly moving relative to each other
    // to execute a scale gesture or similar.
    // Without acceleration, a full swipe of the touch pad diagonal in zoom mode
    // will cover this portion of the display diagonal.
    float pointerGestureZoomSpeedRatio;

    // True to show the location of touches on the touch screen as spots.
    bool showTouches;

    // True if pointer capture is enabled.
    bool pointerCapture;

    // The set of currently disabled input devices.
    SortedVector<int32_t> disabledDevices;

    InputReaderConfiguration() :
            virtualKeyQuietTime(0),
            pointerVelocityControlParameters(1.0f, 500.0f, 3000.0f, 3.0f),
            wheelVelocityControlParameters(1.0f, 15.0f, 50.0f, 4.0f),
            pointerGesturesEnabled(true),
            pointerGestureQuietInterval(100 * 1000000LL), // 100 ms
            pointerGestureDragMinSwitchSpeed(50), // 50 pixels per second
            pointerGestureTapInterval(150 * 1000000LL), // 150 ms
            pointerGestureTapDragInterval(150 * 1000000LL), // 150 ms
            pointerGestureTapSlop(10.0f), // 10 pixels
            pointerGestureMultitouchSettleInterval(100 * 1000000LL), // 100 ms
            pointerGestureMultitouchMinDistance(15), // 15 pixels
            pointerGestureSwipeTransitionAngleCosine(0.2588f), // cosine of 75 degrees
            pointerGestureSwipeMaxWidthRatio(0.25f),
            pointerGestureMovementSpeedRatio(0.8f),
            pointerGestureZoomSpeedRatio(0.3f),
            showTouches(false) { }

    bool getDisplayViewport(ViewportType viewportType, const String8* displayId,
            DisplayViewport* outViewport) const;
    void setPhysicalDisplayViewport(ViewportType viewportType, const DisplayViewport& viewport);
    void setVirtualDisplayViewports(const Vector<DisplayViewport>& viewports);


    void dump(std::string& dump) const;
    void dumpViewport(std::string& dump, const DisplayViewport& viewport) const;

private:
    DisplayViewport mInternalDisplay;
    DisplayViewport mExternalDisplay;
    Vector<DisplayViewport> mVirtualDisplays;
};


struct TouchAffineTransformation {
    float x_scale;
    float x_ymix;
    float x_offset;
    float y_xmix;
    float y_scale;
    float y_offset;

    TouchAffineTransformation() :
        x_scale(1.0f), x_ymix(0.0f), x_offset(0.0f),
        y_xmix(0.0f), y_scale(1.0f), y_offset(0.0f) {
    }

    TouchAffineTransformation(float xscale, float xymix, float xoffset,
            float yxmix, float yscale, float yoffset) :
        x_scale(xscale), x_ymix(xymix), x_offset(xoffset),
        y_xmix(yxmix), y_scale(yscale), y_offset(yoffset) {
    }

    void applyTo(float& x, float& y) const;
};


/*
 * Input reader policy interface.
 *
 * The input reader policy is used by the input reader to interact with the Window Manager
 * and other system components.
 *
 * The actual implementation is partially supported by callbacks into the DVM
 * via JNI.  This interface is also mocked in the unit tests.
 *
 * These methods must NOT re-enter the input reader since they may be called while
 * holding the input reader lock.
 */
class InputReaderPolicyInterface : public virtual RefBase {
protected:
    InputReaderPolicyInterface() { }
    virtual ~InputReaderPolicyInterface() { }

public:
    /* Gets the input reader configuration. */
    virtual void getReaderConfiguration(InputReaderConfiguration* outConfig) = 0;

    /* Gets a pointer controller associated with the specified cursor device (ie. a mouse). */
    virtual sp<PointerControllerInterface> obtainPointerController(int32_t deviceId) = 0;

    /* Notifies the input reader policy that some input devices have changed
     * and provides information about all current input devices.
     */
    virtual void notifyInputDevicesChanged(const Vector<InputDeviceInfo>& inputDevices) = 0;

    /* Gets the keyboard layout for a particular input device. */
    virtual sp<KeyCharacterMap> getKeyboardLayoutOverlay(
            const InputDeviceIdentifier& identifier) = 0;

    /* Gets a user-supplied alias for a particular input device, or an empty string if none. */
    virtual String8 getDeviceAlias(const InputDeviceIdentifier& identifier) = 0;

    /* Gets the affine calibration associated with the specified device. */
    virtual TouchAffineTransformation getTouchAffineTransformation(
            const String8& inputDeviceDescriptor, int32_t surfaceRotation) = 0;
};


/* Processes raw input events and sends cooked event data to an input listener. */
class InputReaderInterface : public virtual RefBase {
protected:
    InputReaderInterface() { }
    virtual ~InputReaderInterface() { }

public:
    /* Dumps the state of the input reader.
     *
     * This method may be called on any thread (usually by the input manager). */
    virtual void dump(std::string& dump) = 0;

    /* Called by the heatbeat to ensures that the reader has not deadlocked. */
    virtual void monitor() = 0;

    /* Returns true if the input device is enabled. */
    virtual bool isInputDeviceEnabled(int32_t deviceId) = 0;

    /* Runs a single iteration of the processing loop.
     * Nominally reads and processes one incoming message from the EventHub.
     *
     * This method should be called on the input reader thread.
     */
    virtual void loopOnce() = 0;

    /* Gets information about all input devices.
     *
     * This method may be called on any thread (usually by the input manager).
     */
    virtual void getInputDevices(Vector<InputDeviceInfo>& outInputDevices) = 0;

    /* Query current input state. */
    virtual int32_t getScanCodeState(int32_t deviceId, uint32_t sourceMask,
            int32_t scanCode) = 0;
    virtual int32_t getKeyCodeState(int32_t deviceId, uint32_t sourceMask,
            int32_t keyCode) = 0;
    virtual int32_t getSwitchState(int32_t deviceId, uint32_t sourceMask,
            int32_t sw) = 0;

    /* Toggle Caps Lock */
    virtual void toggleCapsLockState(int32_t deviceId) = 0;

    /* Determine whether physical keys exist for the given framework-domain key codes. */
    virtual bool hasKeys(int32_t deviceId, uint32_t sourceMask,
            size_t numCodes, const int32_t* keyCodes, uint8_t* outFlags) = 0;

    /* Requests that a reconfiguration of all input devices.
     * The changes flag is a bitfield that indicates what has changed and whether
     * the input devices must all be reopened. */
    virtual void requestRefreshConfiguration(uint32_t changes) = 0;

    /* Controls the vibrator of a particular input device. */
    virtual void vibrate(int32_t deviceId, const nsecs_t* pattern, size_t patternSize,
            ssize_t repeat, int32_t token) = 0;
    virtual void cancelVibrate(int32_t deviceId, int32_t token) = 0;
};

struct StylusState {
    /* Time the stylus event was received. */
    nsecs_t when;
    /* Pressure as reported by the stylus, normalized to the range [0, 1.0]. */
    float pressure;
    /* The state of the stylus buttons as a bitfield (e.g. AMOTION_EVENT_BUTTON_SECONDARY). */
    uint32_t buttons;
    /* Which tool type the stylus is currently using (e.g. AMOTION_EVENT_TOOL_TYPE_ERASER). */
    int32_t toolType;

    void copyFrom(const StylusState& other) {
        when = other.when;
        pressure = other.pressure;
        buttons = other.buttons;
        toolType = other.toolType;
    }

    void clear() {
        when = LLONG_MAX;
        pressure = 0.f;
        buttons = 0;
        toolType = AMOTION_EVENT_TOOL_TYPE_UNKNOWN;
    }
};


/* Internal interface used by individual input devices to access global input device state
 * and parameters maintained by the input reader.
 */
class InputReaderContext {
public:
    InputReaderContext() { }
    virtual ~InputReaderContext() { }

    virtual void updateGlobalMetaState() = 0;
    virtual int32_t getGlobalMetaState() = 0;

    virtual void disableVirtualKeysUntil(nsecs_t time) = 0;
    virtual bool shouldDropVirtualKey(nsecs_t now,
            InputDevice* device, int32_t keyCode, int32_t scanCode) = 0;

    virtual void fadePointer() = 0;

    virtual void requestTimeoutAtTime(nsecs_t when) = 0;
    virtual int32_t bumpGeneration() = 0;

    virtual void getExternalStylusDevices(Vector<InputDeviceInfo>& outDevices) = 0;
    virtual void dispatchExternalStylusState(const StylusState& outState) = 0;

    virtual InputReaderPolicyInterface* getPolicy() = 0;
    virtual InputListenerInterface* getListener() = 0;
    virtual EventHubInterface* getEventHub() = 0;
};


/* The input reader reads raw event data from the event hub and processes it into input events
 * that it sends to the input listener.  Some functions of the input reader, such as early
 * event filtering in low power states, are controlled by a separate policy object.
 *
 * The InputReader owns a collection of InputMappers.  Most of the work it does happens
 * on the input reader thread but the InputReader can receive queries from other system
 * components running on arbitrary threads.  To keep things manageable, the InputReader
 * uses a single Mutex to guard its state.  The Mutex may be held while calling into the
 * EventHub or the InputReaderPolicy but it is never held while calling into the
 * InputListener.
 */
class InputReader : public InputReaderInterface {
public:
    InputReader(const sp<EventHubInterface>& eventHub,
            const sp<InputReaderPolicyInterface>& policy,
            const sp<InputListenerInterface>& listener);
    virtual ~InputReader();

    virtual void dump(std::string& dump);
    virtual void monitor();

    virtual void loopOnce();

    virtual void getInputDevices(Vector<InputDeviceInfo>& outInputDevices);

    virtual bool isInputDeviceEnabled(int32_t deviceId);

    virtual int32_t getScanCodeState(int32_t deviceId, uint32_t sourceMask,
            int32_t scanCode);
    virtual int32_t getKeyCodeState(int32_t deviceId, uint32_t sourceMask,
            int32_t keyCode);
    virtual int32_t getSwitchState(int32_t deviceId, uint32_t sourceMask,
            int32_t sw);

    virtual void toggleCapsLockState(int32_t deviceId);

    virtual bool hasKeys(int32_t deviceId, uint32_t sourceMask,
            size_t numCodes, const int32_t* keyCodes, uint8_t* outFlags);

    virtual void requestRefreshConfiguration(uint32_t changes);

    virtual void vibrate(int32_t deviceId, const nsecs_t* pattern, size_t patternSize,
            ssize_t repeat, int32_t token);
    virtual void cancelVibrate(int32_t deviceId, int32_t token);

protected:
    // These members are protected so they can be instrumented by test cases.
    virtual InputDevice* createDeviceLocked(int32_t deviceId, int32_t controllerNumber,
            const InputDeviceIdentifier& identifier, uint32_t classes);

    class ContextImpl : public InputReaderContext {
        InputReader* mReader;

    public:
        explicit ContextImpl(InputReader* reader);

        virtual void updateGlobalMetaState();
        virtual int32_t getGlobalMetaState();
        virtual void disableVirtualKeysUntil(nsecs_t time);
        virtual bool shouldDropVirtualKey(nsecs_t now,
                InputDevice* device, int32_t keyCode, int32_t scanCode);
        virtual void fadePointer();
        virtual void requestTimeoutAtTime(nsecs_t when);
        virtual int32_t bumpGeneration();
        virtual void getExternalStylusDevices(Vector<InputDeviceInfo>& outDevices);
        virtual void dispatchExternalStylusState(const StylusState& outState);
        virtual InputReaderPolicyInterface* getPolicy();
        virtual InputListenerInterface* getListener();
        virtual EventHubInterface* getEventHub();
    } mContext;

    friend class ContextImpl;

private:
    Mutex mLock;

    Condition mReaderIsAliveCondition;

    sp<EventHubInterface> mEventHub;
    sp<InputReaderPolicyInterface> mPolicy;
    sp<QueuedInputListener> mQueuedListener;

    InputReaderConfiguration mConfig;

    // The event queue.
    static const int EVENT_BUFFER_SIZE = 256;
    RawEvent mEventBuffer[EVENT_BUFFER_SIZE];

    KeyedVector<int32_t, InputDevice*> mDevices;

    // low-level input event decoding and device management
    void processEventsLocked(const RawEvent* rawEvents, size_t count);

    void addDeviceLocked(nsecs_t when, int32_t deviceId);
    void removeDeviceLocked(nsecs_t when, int32_t deviceId);
    void processEventsForDeviceLocked(int32_t deviceId, const RawEvent* rawEvents, size_t count);
    void timeoutExpiredLocked(nsecs_t when);

    void handleConfigurationChangedLocked(nsecs_t when);

    int32_t mGlobalMetaState;
    void updateGlobalMetaStateLocked();
    int32_t getGlobalMetaStateLocked();

    void notifyExternalStylusPresenceChanged();
    void getExternalStylusDevicesLocked(Vector<InputDeviceInfo>& outDevices);
    void dispatchExternalStylusState(const StylusState& state);

    void fadePointerLocked();

    int32_t mGeneration;
    int32_t bumpGenerationLocked();

    void getInputDevicesLocked(Vector<InputDeviceInfo>& outInputDevices);

    nsecs_t mDisableVirtualKeysTimeout;
    void disableVirtualKeysUntilLocked(nsecs_t time);
    bool shouldDropVirtualKeyLocked(nsecs_t now,
            InputDevice* device, int32_t keyCode, int32_t scanCode);

    nsecs_t mNextTimeout;
    void requestTimeoutAtTimeLocked(nsecs_t when);

    uint32_t mConfigurationChangesToRefresh;
    void refreshConfigurationLocked(uint32_t changes);

    // state queries
    typedef int32_t (InputDevice::*GetStateFunc)(uint32_t sourceMask, int32_t code);
    int32_t getStateLocked(int32_t deviceId, uint32_t sourceMask, int32_t code,
            GetStateFunc getStateFunc);
    bool markSupportedKeyCodesLocked(int32_t deviceId, uint32_t sourceMask, size_t numCodes,
            const int32_t* keyCodes, uint8_t* outFlags);
};


/* Reads raw events from the event hub and processes them, endlessly. */
class InputReaderThread : public Thread {
public:
    explicit InputReaderThread(const sp<InputReaderInterface>& reader);
    virtual ~InputReaderThread();

private:
    sp<InputReaderInterface> mReader;

    virtual bool threadLoop();
};


/* Represents the state of a single input device. */
class InputDevice {
public:
    InputDevice(InputReaderContext* context, int32_t id, int32_t generation, int32_t
            controllerNumber, const InputDeviceIdentifier& identifier, uint32_t classes);
    ~InputDevice();

    inline InputReaderContext* getContext() { return mContext; }
    inline int32_t getId() const { return mId; }
    inline int32_t getControllerNumber() const { return mControllerNumber; }
    inline int32_t getGeneration() const { return mGeneration; }
    inline const String8& getName() const { return mIdentifier.name; }
    inline const String8& getDescriptor() { return mIdentifier.descriptor; }
    inline uint32_t getClasses() const { return mClasses; }
    inline uint32_t getSources() const { return mSources; }

    inline bool isExternal() { return mIsExternal; }
    inline void setExternal(bool external) { mIsExternal = external; }

    inline void setMic(bool hasMic) { mHasMic = hasMic; }
    inline bool hasMic() const { return mHasMic; }

    inline bool isIgnored() { return mMappers.isEmpty(); }

    bool isEnabled();
    void setEnabled(bool enabled, nsecs_t when);

    void dump(std::string& dump);
    void addMapper(InputMapper* mapper);
    void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    void reset(nsecs_t when);
    void process(const RawEvent* rawEvents, size_t count);
    void timeoutExpired(nsecs_t when);
    void updateExternalStylusState(const StylusState& state);

    void getDeviceInfo(InputDeviceInfo* outDeviceInfo);
    int32_t getKeyCodeState(uint32_t sourceMask, int32_t keyCode);
    int32_t getScanCodeState(uint32_t sourceMask, int32_t scanCode);
    int32_t getSwitchState(uint32_t sourceMask, int32_t switchCode);
    bool markSupportedKeyCodes(uint32_t sourceMask, size_t numCodes,
            const int32_t* keyCodes, uint8_t* outFlags);
    void vibrate(const nsecs_t* pattern, size_t patternSize, ssize_t repeat, int32_t token);
    void cancelVibrate(int32_t token);
    void cancelTouch(nsecs_t when);

    int32_t getMetaState();
    void updateMetaState(int32_t keyCode);

    void fadePointer();

    void bumpGeneration();

    void notifyReset(nsecs_t when);

    inline const PropertyMap& getConfiguration() { return mConfiguration; }
    inline EventHubInterface* getEventHub() { return mContext->getEventHub(); }

    bool hasKey(int32_t code) {
        return getEventHub()->hasScanCode(mId, code);
    }

    bool hasAbsoluteAxis(int32_t code) {
        RawAbsoluteAxisInfo info;
        getEventHub()->getAbsoluteAxisInfo(mId, code, &info);
        return info.valid;
    }

    bool isKeyPressed(int32_t code) {
        return getEventHub()->getScanCodeState(mId, code) == AKEY_STATE_DOWN;
    }

    int32_t getAbsoluteAxisValue(int32_t code) {
        int32_t value;
        getEventHub()->getAbsoluteAxisValue(mId, code, &value);
        return value;
    }

private:
    InputReaderContext* mContext;
    int32_t mId;
    int32_t mGeneration;
    int32_t mControllerNumber;
    InputDeviceIdentifier mIdentifier;
    String8 mAlias;
    uint32_t mClasses;

    Vector<InputMapper*> mMappers;

    uint32_t mSources;
    bool mIsExternal;
    bool mHasMic;
    bool mDropUntilNextSync;

    typedef int32_t (InputMapper::*GetStateFunc)(uint32_t sourceMask, int32_t code);
    int32_t getState(uint32_t sourceMask, int32_t code, GetStateFunc getStateFunc);

    PropertyMap mConfiguration;
};


/* Keeps track of the state of mouse or touch pad buttons. */
class CursorButtonAccumulator {
public:
    CursorButtonAccumulator();
    void reset(InputDevice* device);

    void process(const RawEvent* rawEvent);

    uint32_t getButtonState() const;

private:
    bool mBtnLeft;
    bool mBtnRight;
    bool mBtnMiddle;
    bool mBtnBack;
    bool mBtnSide;
    bool mBtnForward;
    bool mBtnExtra;
    bool mBtnTask;

    void clearButtons();
};


/* Keeps track of cursor movements. */

class CursorMotionAccumulator {
public:
    CursorMotionAccumulator();
    void reset(InputDevice* device);

    void process(const RawEvent* rawEvent);
    void finishSync();

    inline int32_t getRelativeX() const { return mRelX; }
    inline int32_t getRelativeY() const { return mRelY; }

private:
    int32_t mRelX;
    int32_t mRelY;

    void clearRelativeAxes();
};


/* Keeps track of cursor scrolling motions. */

class CursorScrollAccumulator {
public:
    CursorScrollAccumulator();
    void configure(InputDevice* device);
    void reset(InputDevice* device);

    void process(const RawEvent* rawEvent);
    void finishSync();

    inline bool haveRelativeVWheel() const { return mHaveRelWheel; }
    inline bool haveRelativeHWheel() const { return mHaveRelHWheel; }

    inline int32_t getRelativeX() const { return mRelX; }
    inline int32_t getRelativeY() const { return mRelY; }
    inline int32_t getRelativeVWheel() const { return mRelWheel; }
    inline int32_t getRelativeHWheel() const { return mRelHWheel; }

private:
    bool mHaveRelWheel;
    bool mHaveRelHWheel;

    int32_t mRelX;
    int32_t mRelY;
    int32_t mRelWheel;
    int32_t mRelHWheel;

    void clearRelativeAxes();
};


/* Keeps track of the state of touch, stylus and tool buttons. */
class TouchButtonAccumulator {
public:
    TouchButtonAccumulator();
    void configure(InputDevice* device);
    void reset(InputDevice* device);

    void process(const RawEvent* rawEvent);

    uint32_t getButtonState() const;
    int32_t getToolType() const;
    bool isToolActive() const;
    bool isHovering() const;
    bool hasStylus() const;

private:
    bool mHaveBtnTouch;
    bool mHaveStylus;

    bool mBtnTouch;
    bool mBtnStylus;
    bool mBtnStylus2;
    bool mBtnToolFinger;
    bool mBtnToolPen;
    bool mBtnToolRubber;
    bool mBtnToolBrush;
    bool mBtnToolPencil;
    bool mBtnToolAirbrush;
    bool mBtnToolMouse;
    bool mBtnToolLens;
    bool mBtnToolDoubleTap;
    bool mBtnToolTripleTap;
    bool mBtnToolQuadTap;

    void clearButtons();
};


/* Raw axis information from the driver. */
struct RawPointerAxes {
    RawAbsoluteAxisInfo x;
    RawAbsoluteAxisInfo y;
    RawAbsoluteAxisInfo pressure;
    RawAbsoluteAxisInfo touchMajor;
    RawAbsoluteAxisInfo touchMinor;
    RawAbsoluteAxisInfo toolMajor;
    RawAbsoluteAxisInfo toolMinor;
    RawAbsoluteAxisInfo orientation;
    RawAbsoluteAxisInfo distance;
    RawAbsoluteAxisInfo tiltX;
    RawAbsoluteAxisInfo tiltY;
    RawAbsoluteAxisInfo trackingId;
    RawAbsoluteAxisInfo slot;

    RawPointerAxes();
    void clear();
};


/* Raw data for a collection of pointers including a pointer id mapping table. */
struct RawPointerData {
    struct Pointer {
        uint32_t id;
        int32_t x;
        int32_t y;
        int32_t pressure;
        int32_t touchMajor;
        int32_t touchMinor;
        int32_t toolMajor;
        int32_t toolMinor;
        int32_t orientation;
        int32_t distance;
        int32_t tiltX;
        int32_t tiltY;
        int32_t toolType; // a fully decoded AMOTION_EVENT_TOOL_TYPE constant
        bool isHovering;
    };

    uint32_t pointerCount;
    Pointer pointers[MAX_POINTERS];
    BitSet32 hoveringIdBits, touchingIdBits;
    uint32_t idToIndex[MAX_POINTER_ID + 1];

    RawPointerData();
    void clear();
    void copyFrom(const RawPointerData& other);
    void getCentroidOfTouchingPointers(float* outX, float* outY) const;

    inline void markIdBit(uint32_t id, bool isHovering) {
        if (isHovering) {
            hoveringIdBits.markBit(id);
        } else {
            touchingIdBits.markBit(id);
        }
    }

    inline void clearIdBits() {
        hoveringIdBits.clear();
        touchingIdBits.clear();
    }

    inline const Pointer& pointerForId(uint32_t id) const {
        return pointers[idToIndex[id]];
    }

    inline bool isHovering(uint32_t pointerIndex) {
        return pointers[pointerIndex].isHovering;
    }
};


/* Cooked data for a collection of pointers including a pointer id mapping table. */
struct CookedPointerData {
    uint32_t pointerCount;
    PointerProperties pointerProperties[MAX_POINTERS];
    PointerCoords pointerCoords[MAX_POINTERS];
    BitSet32 hoveringIdBits, touchingIdBits;
    uint32_t idToIndex[MAX_POINTER_ID + 1];

    CookedPointerData();
    void clear();
    void copyFrom(const CookedPointerData& other);

    inline const PointerCoords& pointerCoordsForId(uint32_t id) const {
        return pointerCoords[idToIndex[id]];
    }

    inline PointerCoords& editPointerCoordsWithId(uint32_t id) {
        return pointerCoords[idToIndex[id]];
    }

    inline PointerProperties& editPointerPropertiesWithId(uint32_t id) {
        return pointerProperties[idToIndex[id]];
    }

    inline bool isHovering(uint32_t pointerIndex) const {
        return hoveringIdBits.hasBit(pointerProperties[pointerIndex].id);
    }

    inline bool isTouching(uint32_t pointerIndex) const {
        return touchingIdBits.hasBit(pointerProperties[pointerIndex].id);
    }
};


/* Keeps track of the state of single-touch protocol. */
class SingleTouchMotionAccumulator {
public:
    SingleTouchMotionAccumulator();

    void process(const RawEvent* rawEvent);
    void reset(InputDevice* device);

    inline int32_t getAbsoluteX() const { return mAbsX; }
    inline int32_t getAbsoluteY() const { return mAbsY; }
    inline int32_t getAbsolutePressure() const { return mAbsPressure; }
    inline int32_t getAbsoluteToolWidth() const { return mAbsToolWidth; }
    inline int32_t getAbsoluteDistance() const { return mAbsDistance; }
    inline int32_t getAbsoluteTiltX() const { return mAbsTiltX; }
    inline int32_t getAbsoluteTiltY() const { return mAbsTiltY; }

private:
    int32_t mAbsX;
    int32_t mAbsY;
    int32_t mAbsPressure;
    int32_t mAbsToolWidth;
    int32_t mAbsDistance;
    int32_t mAbsTiltX;
    int32_t mAbsTiltY;

    void clearAbsoluteAxes();
};


/* Keeps track of the state of multi-touch protocol. */
class MultiTouchMotionAccumulator {
public:
    class Slot {
    public:
        inline bool isInUse() const { return mInUse; }
        inline int32_t getX() const { return mAbsMTPositionX; }
        inline int32_t getY() const { return mAbsMTPositionY; }
        inline int32_t getTouchMajor() const { return mAbsMTTouchMajor; }
        inline int32_t getTouchMinor() const {
            return mHaveAbsMTTouchMinor ? mAbsMTTouchMinor : mAbsMTTouchMajor; }
        inline int32_t getToolMajor() const { return mAbsMTWidthMajor; }
        inline int32_t getToolMinor() const {
            return mHaveAbsMTWidthMinor ? mAbsMTWidthMinor : mAbsMTWidthMajor; }
        inline int32_t getOrientation() const { return mAbsMTOrientation; }
        inline int32_t getTrackingId() const { return mAbsMTTrackingId; }
        inline int32_t getPressure() const { return mAbsMTPressure; }
        inline int32_t getDistance() const { return mAbsMTDistance; }
        inline int32_t getToolType() const;

    private:
        friend class MultiTouchMotionAccumulator;

        bool mInUse;
        bool mHaveAbsMTTouchMinor;
        bool mHaveAbsMTWidthMinor;
        bool mHaveAbsMTToolType;

        int32_t mAbsMTPositionX;
        int32_t mAbsMTPositionY;
        int32_t mAbsMTTouchMajor;
        int32_t mAbsMTTouchMinor;
        int32_t mAbsMTWidthMajor;
        int32_t mAbsMTWidthMinor;
        int32_t mAbsMTOrientation;
        int32_t mAbsMTTrackingId;
        int32_t mAbsMTPressure;
        int32_t mAbsMTDistance;
        int32_t mAbsMTToolType;

        Slot();
        void clear();
    };

    MultiTouchMotionAccumulator();
    ~MultiTouchMotionAccumulator();

    void configure(InputDevice* device, size_t slotCount, bool usingSlotsProtocol);
    void reset(InputDevice* device);
    void process(const RawEvent* rawEvent);
    void finishSync();
    bool hasStylus() const;

    inline size_t getSlotCount() const { return mSlotCount; }
    inline const Slot* getSlot(size_t index) const { return &mSlots[index]; }
    inline uint32_t getDeviceTimestamp() const { return mDeviceTimestamp; }

private:
    int32_t mCurrentSlot;
    Slot* mSlots;
    size_t mSlotCount;
    bool mUsingSlotsProtocol;
    bool mHaveStylus;
    uint32_t mDeviceTimestamp;

    void clearSlots(int32_t initialSlot);
    void setDefaultPressure(int pressure);
};


/* An input mapper transforms raw input events into cooked event data.
 * A single input device can have multiple associated input mappers in order to interpret
 * different classes of events.
 *
 * InputMapper lifecycle:
 * - create
 * - configure with 0 changes
 * - reset
 * - process, process, process (may occasionally reconfigure with non-zero changes or reset)
 * - reset
 * - destroy
 */
class InputMapper {
public:
    explicit InputMapper(InputDevice* device);
    virtual ~InputMapper();

    inline InputDevice* getDevice() { return mDevice; }
    inline int32_t getDeviceId() { return mDevice->getId(); }
    inline const String8 getDeviceName() { return mDevice->getName(); }
    inline InputReaderContext* getContext() { return mContext; }
    inline InputReaderPolicyInterface* getPolicy() { return mContext->getPolicy(); }
    inline InputListenerInterface* getListener() { return mContext->getListener(); }
    inline EventHubInterface* getEventHub() { return mContext->getEventHub(); }

    virtual uint32_t getSources() = 0;
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent) = 0;
    virtual void timeoutExpired(nsecs_t when);

    virtual int32_t getKeyCodeState(uint32_t sourceMask, int32_t keyCode);
    virtual int32_t getScanCodeState(uint32_t sourceMask, int32_t scanCode);
    virtual int32_t getSwitchState(uint32_t sourceMask, int32_t switchCode);
    virtual bool markSupportedKeyCodes(uint32_t sourceMask, size_t numCodes,
            const int32_t* keyCodes, uint8_t* outFlags);
    virtual void vibrate(const nsecs_t* pattern, size_t patternSize, ssize_t repeat,
            int32_t token);
    virtual void cancelVibrate(int32_t token);
    virtual void cancelTouch(nsecs_t when);

    virtual int32_t getMetaState();
    virtual void updateMetaState(int32_t keyCode);

    virtual void updateExternalStylusState(const StylusState& state);

    virtual void fadePointer();

protected:
    InputDevice* mDevice;
    InputReaderContext* mContext;

    status_t getAbsoluteAxisInfo(int32_t axis, RawAbsoluteAxisInfo* axisInfo);
    void bumpGeneration();

    static void dumpRawAbsoluteAxisInfo(std::string& dump,
            const RawAbsoluteAxisInfo& axis, const char* name);
    static void dumpStylusState(std::string& dump, const StylusState& state);
};


class SwitchInputMapper : public InputMapper {
public:
    explicit SwitchInputMapper(InputDevice* device);
    virtual ~SwitchInputMapper();

    virtual uint32_t getSources();
    virtual void process(const RawEvent* rawEvent);

    virtual int32_t getSwitchState(uint32_t sourceMask, int32_t switchCode);
    virtual void dump(std::string& dump);

private:
    uint32_t mSwitchValues;
    uint32_t mUpdatedSwitchMask;

    void processSwitch(int32_t switchCode, int32_t switchValue);
    void sync(nsecs_t when);
};


class VibratorInputMapper : public InputMapper {
public:
    explicit VibratorInputMapper(InputDevice* device);
    virtual ~VibratorInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void process(const RawEvent* rawEvent);

    virtual void vibrate(const nsecs_t* pattern, size_t patternSize, ssize_t repeat,
            int32_t token);
    virtual void cancelVibrate(int32_t token);
    virtual void timeoutExpired(nsecs_t when);
    virtual void dump(std::string& dump);

private:
    bool mVibrating;
    nsecs_t mPattern[MAX_VIBRATE_PATTERN_SIZE];
    size_t mPatternSize;
    ssize_t mRepeat;
    int32_t mToken;
    ssize_t mIndex;
    nsecs_t mNextStepTime;

    void nextStep();
    void stopVibrating();
};


class KeyboardInputMapper : public InputMapper {
public:
    KeyboardInputMapper(InputDevice* device, uint32_t source, int32_t keyboardType);
    virtual ~KeyboardInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

    virtual int32_t getKeyCodeState(uint32_t sourceMask, int32_t keyCode);
    virtual int32_t getScanCodeState(uint32_t sourceMask, int32_t scanCode);
    virtual bool markSupportedKeyCodes(uint32_t sourceMask, size_t numCodes,
            const int32_t* keyCodes, uint8_t* outFlags);

    virtual int32_t getMetaState();
    virtual void updateMetaState(int32_t keyCode);

private:
    struct KeyDown {
        int32_t keyCode;
        int32_t scanCode;
    };

    uint32_t mSource;
    int32_t mKeyboardType;

    int32_t mOrientation; // orientation for dpad keys

    Vector<KeyDown> mKeyDowns; // keys that are down
    int32_t mMetaState;
    nsecs_t mDownTime; // time of most recent key down

    int32_t mCurrentHidUsage; // most recent HID usage seen this packet, or 0 if none

    struct LedState {
        bool avail; // led is available
        bool on;    // we think the led is currently on
    };
    LedState mCapsLockLedState;
    LedState mNumLockLedState;
    LedState mScrollLockLedState;

    // Immutable configuration parameters.
    struct Parameters {
        bool hasAssociatedDisplay;
        bool orientationAware;
        bool handlesKeyRepeat;
    } mParameters;

    void configureParameters();
    void dumpParameters(std::string& dump);

    bool isKeyboardOrGamepadKey(int32_t scanCode);
    bool isMediaKey(int32_t keyCode);

    void processKey(nsecs_t when, bool down, int32_t scanCode, int32_t usageCode);

    bool updateMetaStateIfNeeded(int32_t keyCode, bool down);

    ssize_t findKeyDown(int32_t scanCode);

    void resetLedState();
    void initializeLedState(LedState& ledState, int32_t led);
    void updateLedState(bool reset);
    void updateLedStateForModifier(LedState& ledState, int32_t led,
            int32_t modifier, bool reset);
};


class CursorInputMapper : public InputMapper {
public:
    explicit CursorInputMapper(InputDevice* device);
    virtual ~CursorInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

    virtual int32_t getScanCodeState(uint32_t sourceMask, int32_t scanCode);

    virtual void fadePointer();

private:
    // Amount that trackball needs to move in order to generate a key event.
    static const int32_t TRACKBALL_MOVEMENT_THRESHOLD = 6;

    // Immutable configuration parameters.
    struct Parameters {
        enum Mode {
            MODE_POINTER,
            MODE_POINTER_RELATIVE,
            MODE_NAVIGATION,
        };

        Mode mode;
        bool hasAssociatedDisplay;
        bool orientationAware;
    } mParameters;

    CursorButtonAccumulator mCursorButtonAccumulator;
    CursorMotionAccumulator mCursorMotionAccumulator;
    CursorScrollAccumulator mCursorScrollAccumulator;

    int32_t mSource;
    float mXScale;
    float mYScale;
    float mXPrecision;
    float mYPrecision;

    float mVWheelScale;
    float mHWheelScale;

    // Velocity controls for mouse pointer and wheel movements.
    // The controls for X and Y wheel movements are separate to keep them decoupled.
    VelocityControl mPointerVelocityControl;
    VelocityControl mWheelXVelocityControl;
    VelocityControl mWheelYVelocityControl;

    int32_t mOrientation;

    sp<PointerControllerInterface> mPointerController;

    int32_t mButtonState;
    nsecs_t mDownTime;

    void configureParameters();
    void dumpParameters(std::string& dump);

    void sync(nsecs_t when);
};


class RotaryEncoderInputMapper : public InputMapper {
public:
    explicit RotaryEncoderInputMapper(InputDevice* device);
    virtual ~RotaryEncoderInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

private:
    CursorScrollAccumulator mRotaryEncoderScrollAccumulator;

    int32_t mSource;
    float mScalingFactor;
    int32_t mOrientation;

    void sync(nsecs_t when);
};

class TouchInputMapper : public InputMapper {
public:
    explicit TouchInputMapper(InputDevice* device);
    virtual ~TouchInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

    virtual int32_t getKeyCodeState(uint32_t sourceMask, int32_t keyCode);
    virtual int32_t getScanCodeState(uint32_t sourceMask, int32_t scanCode);
    virtual bool markSupportedKeyCodes(uint32_t sourceMask, size_t numCodes,
            const int32_t* keyCodes, uint8_t* outFlags);

    virtual void fadePointer();
    virtual void cancelTouch(nsecs_t when);
    virtual void timeoutExpired(nsecs_t when);
    virtual void updateExternalStylusState(const StylusState& state);

protected:
    CursorButtonAccumulator mCursorButtonAccumulator;
    CursorScrollAccumulator mCursorScrollAccumulator;
    TouchButtonAccumulator mTouchButtonAccumulator;

    struct VirtualKey {
        int32_t keyCode;
        int32_t scanCode;
        uint32_t flags;

        // computed hit box, specified in touch screen coords based on known display size
        int32_t hitLeft;
        int32_t hitTop;
        int32_t hitRight;
        int32_t hitBottom;

        inline bool isHit(int32_t x, int32_t y) const {
            return x >= hitLeft && x <= hitRight && y >= hitTop && y <= hitBottom;
        }
    };

    // Input sources and device mode.
    uint32_t mSource;

    enum DeviceMode {
        DEVICE_MODE_DISABLED, // input is disabled
        DEVICE_MODE_DIRECT, // direct mapping (touchscreen)
        DEVICE_MODE_UNSCALED, // unscaled mapping (touchpad)
        DEVICE_MODE_NAVIGATION, // unscaled mapping with assist gesture (touch navigation)
        DEVICE_MODE_POINTER, // pointer mapping (pointer)
    };
    DeviceMode mDeviceMode;

    // The reader's configuration.
    InputReaderConfiguration mConfig;

    // Immutable configuration parameters.
    struct Parameters {
        enum DeviceType {
            DEVICE_TYPE_TOUCH_SCREEN,
            DEVICE_TYPE_TOUCH_PAD,
            DEVICE_TYPE_TOUCH_NAVIGATION,
            DEVICE_TYPE_POINTER,
        };

        DeviceType deviceType;
        bool hasAssociatedDisplay;
        bool associatedDisplayIsExternal;
        bool orientationAware;
        bool hasButtonUnderPad;
        String8 uniqueDisplayId;

        enum GestureMode {
            GESTURE_MODE_SINGLE_TOUCH,
            GESTURE_MODE_MULTI_TOUCH,
        };
        GestureMode gestureMode;

        bool wake;
    } mParameters;

    // Immutable calibration parameters in parsed form.
    struct Calibration {
        // Size
        enum SizeCalibration {
            SIZE_CALIBRATION_DEFAULT,
            SIZE_CALIBRATION_NONE,
            SIZE_CALIBRATION_GEOMETRIC,
            SIZE_CALIBRATION_DIAMETER,
            SIZE_CALIBRATION_BOX,
            SIZE_CALIBRATION_AREA,
        };

        SizeCalibration sizeCalibration;

        bool haveSizeScale;
        float sizeScale;
        bool haveSizeBias;
        float sizeBias;
        bool haveSizeIsSummed;
        bool sizeIsSummed;

        // Pressure
        enum PressureCalibration {
            PRESSURE_CALIBRATION_DEFAULT,
            PRESSURE_CALIBRATION_NONE,
            PRESSURE_CALIBRATION_PHYSICAL,
            PRESSURE_CALIBRATION_AMPLITUDE,
        };

        PressureCalibration pressureCalibration;
        bool havePressureScale;
        float pressureScale;

        // Orientation
        enum OrientationCalibration {
            ORIENTATION_CALIBRATION_DEFAULT,
            ORIENTATION_CALIBRATION_NONE,
            ORIENTATION_CALIBRATION_INTERPOLATED,
            ORIENTATION_CALIBRATION_VECTOR,
        };

        OrientationCalibration orientationCalibration;

        // Distance
        enum DistanceCalibration {
            DISTANCE_CALIBRATION_DEFAULT,
            DISTANCE_CALIBRATION_NONE,
            DISTANCE_CALIBRATION_SCALED,
        };

        DistanceCalibration distanceCalibration;
        bool haveDistanceScale;
        float distanceScale;

        enum CoverageCalibration {
            COVERAGE_CALIBRATION_DEFAULT,
            COVERAGE_CALIBRATION_NONE,
            COVERAGE_CALIBRATION_BOX,
        };

        CoverageCalibration coverageCalibration;

        inline void applySizeScaleAndBias(float* outSize) const {
            if (haveSizeScale) {
                *outSize *= sizeScale;
            }
            if (haveSizeBias) {
                *outSize += sizeBias;
            }
            if (*outSize < 0) {
                *outSize = 0;
            }
        }
    } mCalibration;

    // Affine location transformation/calibration
    struct TouchAffineTransformation mAffineTransform;

    RawPointerAxes mRawPointerAxes;

    struct RawState {
        nsecs_t when;
        uint32_t deviceTimestamp;

        // Raw pointer sample data.
        RawPointerData rawPointerData;

        int32_t buttonState;

        // Scroll state.
        int32_t rawVScroll;
        int32_t rawHScroll;

        void copyFrom(const RawState& other) {
            when = other.when;
            deviceTimestamp = other.deviceTimestamp;
            rawPointerData.copyFrom(other.rawPointerData);
            buttonState = other.buttonState;
            rawVScroll = other.rawVScroll;
            rawHScroll = other.rawHScroll;
        }

        void clear() {
            when = 0;
            deviceTimestamp = 0;
            rawPointerData.clear();
            buttonState = 0;
            rawVScroll = 0;
            rawHScroll = 0;
        }
    };

    struct CookedState {
        uint32_t deviceTimestamp;
        // Cooked pointer sample data.
        CookedPointerData cookedPointerData;

        // Id bits used to differentiate fingers, stylus and mouse tools.
        BitSet32 fingerIdBits;
        BitSet32 stylusIdBits;
        BitSet32 mouseIdBits;

        int32_t buttonState;

        void copyFrom(const CookedState& other) {
            deviceTimestamp = other.deviceTimestamp;
            cookedPointerData.copyFrom(other.cookedPointerData);
            fingerIdBits = other.fingerIdBits;
            stylusIdBits = other.stylusIdBits;
            mouseIdBits = other.mouseIdBits;
            buttonState = other.buttonState;
        }

        void clear() {
            deviceTimestamp = 0;
            cookedPointerData.clear();
            fingerIdBits.clear();
            stylusIdBits.clear();
            mouseIdBits.clear();
            buttonState = 0;
        }
    };

    Vector<RawState> mRawStatesPending;
    RawState mCurrentRawState;
    CookedState mCurrentCookedState;
    RawState mLastRawState;
    CookedState mLastCookedState;

    // State provided by an external stylus
    StylusState mExternalStylusState;
    int64_t mExternalStylusId;
    nsecs_t mExternalStylusFusionTimeout;
    bool mExternalStylusDataPending;

    // True if we sent a HOVER_ENTER event.
    bool mSentHoverEnter;

    // Have we assigned pointer IDs for this stream
    bool mHavePointerIds;

    // Is the current stream of direct touch events aborted
    bool mCurrentMotionAborted;

    // The time the primary pointer last went down.
    nsecs_t mDownTime;

    // The pointer controller, or null if the device is not a pointer.
    sp<PointerControllerInterface> mPointerController;

    Vector<VirtualKey> mVirtualKeys;

    virtual void configureParameters();
    virtual void dumpParameters(std::string& dump);
    virtual void configureRawPointerAxes();
    virtual void dumpRawPointerAxes(std::string& dump);
    virtual void configureSurface(nsecs_t when, bool* outResetNeeded);
    virtual void dumpSurface(std::string& dump);
    virtual void configureVirtualKeys();
    virtual void dumpVirtualKeys(std::string& dump);
    virtual void parseCalibration();
    virtual void resolveCalibration();
    virtual void dumpCalibration(std::string& dump);
    virtual void updateAffineTransformation();
    virtual void dumpAffineTransformation(std::string& dump);
    virtual void resolveExternalStylusPresence();
    virtual bool hasStylus() const = 0;
    virtual bool hasExternalStylus() const;

    virtual void syncTouch(nsecs_t when, RawState* outState) = 0;

private:
    // The current viewport.
    // The components of the viewport are specified in the display's rotated orientation.
    DisplayViewport mViewport;

    // The surface orientation, width and height set by configureSurface().
    // The width and height are derived from the viewport but are specified
    // in the natural orientation.
    // The surface origin specifies how the surface coordinates should be translated
    // to align with the logical display coordinate space.
    // The orientation may be different from the viewport orientation as it specifies
    // the rotation of the surface coordinates required to produce the viewport's
    // requested orientation, so it will depend on whether the device is orientation aware.
    int32_t mSurfaceWidth;
    int32_t mSurfaceHeight;
    int32_t mSurfaceLeft;
    int32_t mSurfaceTop;
    int32_t mSurfaceOrientation;

    // Translation and scaling factors, orientation-independent.
    float mXTranslate;
    float mXScale;
    float mXPrecision;

    float mYTranslate;
    float mYScale;
    float mYPrecision;

    float mGeometricScale;

    float mPressureScale;

    float mSizeScale;

    float mOrientationScale;

    float mDistanceScale;

    bool mHaveTilt;
    float mTiltXCenter;
    float mTiltXScale;
    float mTiltYCenter;
    float mTiltYScale;

    bool mExternalStylusConnected;

    // Oriented motion ranges for input device info.
    struct OrientedRanges {
        InputDeviceInfo::MotionRange x;
        InputDeviceInfo::MotionRange y;
        InputDeviceInfo::MotionRange pressure;

        bool haveSize;
        InputDeviceInfo::MotionRange size;

        bool haveTouchSize;
        InputDeviceInfo::MotionRange touchMajor;
        InputDeviceInfo::MotionRange touchMinor;

        bool haveToolSize;
        InputDeviceInfo::MotionRange toolMajor;
        InputDeviceInfo::MotionRange toolMinor;

        bool haveOrientation;
        InputDeviceInfo::MotionRange orientation;

        bool haveDistance;
        InputDeviceInfo::MotionRange distance;

        bool haveTilt;
        InputDeviceInfo::MotionRange tilt;

        OrientedRanges() {
            clear();
        }

        void clear() {
            haveSize = false;
            haveTouchSize = false;
            haveToolSize = false;
            haveOrientation = false;
            haveDistance = false;
            haveTilt = false;
        }
    } mOrientedRanges;

    // Oriented dimensions and precision.
    float mOrientedXPrecision;
    float mOrientedYPrecision;

    struct CurrentVirtualKeyState {
        bool down;
        bool ignored;
        nsecs_t downTime;
        int32_t keyCode;
        int32_t scanCode;
    } mCurrentVirtualKey;

    // Scale factor for gesture or mouse based pointer movements.
    float mPointerXMovementScale;
    float mPointerYMovementScale;

    // Scale factor for gesture based zooming and other freeform motions.
    float mPointerXZoomScale;
    float mPointerYZoomScale;

    // The maximum swipe width.
    float mPointerGestureMaxSwipeWidth;

    struct PointerDistanceHeapElement {
        uint32_t currentPointerIndex : 8;
        uint32_t lastPointerIndex : 8;
        uint64_t distance : 48; // squared distance
    };

    enum PointerUsage {
        POINTER_USAGE_NONE,
        POINTER_USAGE_GESTURES,
        POINTER_USAGE_STYLUS,
        POINTER_USAGE_MOUSE,
    };
    PointerUsage mPointerUsage;

    struct PointerGesture {
        enum Mode {
            // No fingers, button is not pressed.
            // Nothing happening.
            NEUTRAL,

            // No fingers, button is not pressed.
            // Tap detected.
            // Emits DOWN and UP events at the pointer location.
            TAP,

            // Exactly one finger dragging following a tap.
            // Pointer follows the active finger.
            // Emits DOWN, MOVE and UP events at the pointer location.
            //
            // Detect double-taps when the finger goes up while in TAP_DRAG mode.
            TAP_DRAG,

            // Button is pressed.
            // Pointer follows the active finger if there is one.  Other fingers are ignored.
            // Emits DOWN, MOVE and UP events at the pointer location.
            BUTTON_CLICK_OR_DRAG,

            // Exactly one finger, button is not pressed.
            // Pointer follows the active finger.
            // Emits HOVER_MOVE events at the pointer location.
            //
            // Detect taps when the finger goes up while in HOVER mode.
            HOVER,

            // Exactly two fingers but neither have moved enough to clearly indicate
            // whether a swipe or freeform gesture was intended.  We consider the
            // pointer to be pressed so this enables clicking or long-pressing on buttons.
            // Pointer does not move.
            // Emits DOWN, MOVE and UP events with a single stationary pointer coordinate.
            PRESS,

            // Exactly two fingers moving in the same direction, button is not pressed.
            // Pointer does not move.
            // Emits DOWN, MOVE and UP events with a single pointer coordinate that
            // follows the midpoint between both fingers.
            SWIPE,

            // Two or more fingers moving in arbitrary directions, button is not pressed.
            // Pointer does not move.
            // Emits DOWN, POINTER_DOWN, MOVE, POINTER_UP and UP events that follow
            // each finger individually relative to the initial centroid of the finger.
            FREEFORM,

            // Waiting for quiet time to end before starting the next gesture.
            QUIET,
        };

        // Time the first finger went down.
        nsecs_t firstTouchTime;

        // The active pointer id from the raw touch data.
        int32_t activeTouchId; // -1 if none

        // The active pointer id from the gesture last delivered to the application.
        int32_t activeGestureId; // -1 if none

        // Pointer coords and ids for the current and previous pointer gesture.
        Mode currentGestureMode;
        BitSet32 currentGestureIdBits;
        uint32_t currentGestureIdToIndex[MAX_POINTER_ID + 1];
        PointerProperties currentGestureProperties[MAX_POINTERS];
        PointerCoords currentGestureCoords[MAX_POINTERS];

        Mode lastGestureMode;
        BitSet32 lastGestureIdBits;
        uint32_t lastGestureIdToIndex[MAX_POINTER_ID + 1];
        PointerProperties lastGestureProperties[MAX_POINTERS];
        PointerCoords lastGestureCoords[MAX_POINTERS];

        // Time the pointer gesture last went down.
        nsecs_t downTime;

        // Time when the pointer went down for a TAP.
        nsecs_t tapDownTime;

        // Time when the pointer went up for a TAP.
        nsecs_t tapUpTime;

        // Location of initial tap.
        float tapX, tapY;

        // Time we started waiting for quiescence.
        nsecs_t quietTime;

        // Reference points for multitouch gestures.
        float referenceTouchX;    // reference touch X/Y coordinates in surface units
        float referenceTouchY;
        float referenceGestureX;  // reference gesture X/Y coordinates in pixels
        float referenceGestureY;

        // Distance that each pointer has traveled which has not yet been
        // subsumed into the reference gesture position.
        BitSet32 referenceIdBits;
        struct Delta {
            float dx, dy;
        };
        Delta referenceDeltas[MAX_POINTER_ID + 1];

        // Describes how touch ids are mapped to gesture ids for freeform gestures.
        uint32_t freeformTouchToGestureIdMap[MAX_POINTER_ID + 1];

        // A velocity tracker for determining whether to switch active pointers during drags.
        VelocityTracker velocityTracker;

        void reset() {
            firstTouchTime = LLONG_MIN;
            activeTouchId = -1;
            activeGestureId = -1;
            currentGestureMode = NEUTRAL;
            currentGestureIdBits.clear();
            lastGestureMode = NEUTRAL;
            lastGestureIdBits.clear();
            downTime = 0;
            velocityTracker.clear();
            resetTap();
            resetQuietTime();
        }

        void resetTap() {
            tapDownTime = LLONG_MIN;
            tapUpTime = LLONG_MIN;
        }

        void resetQuietTime() {
            quietTime = LLONG_MIN;
        }
    } mPointerGesture;

    struct PointerSimple {
        PointerCoords currentCoords;
        PointerProperties currentProperties;
        PointerCoords lastCoords;
        PointerProperties lastProperties;

        // True if the pointer is down.
        bool down;

        // True if the pointer is hovering.
        bool hovering;

        // Time the pointer last went down.
        nsecs_t downTime;

        void reset() {
            currentCoords.clear();
            currentProperties.clear();
            lastCoords.clear();
            lastProperties.clear();
            down = false;
            hovering = false;
            downTime = 0;
        }
    } mPointerSimple;

    // The pointer and scroll velocity controls.
    VelocityControl mPointerVelocityControl;
    VelocityControl mWheelXVelocityControl;
    VelocityControl mWheelYVelocityControl;

    void resetExternalStylus();
    void clearStylusDataPendingFlags();

    void sync(nsecs_t when);

    bool consumeRawTouches(nsecs_t when, uint32_t policyFlags);
    void processRawTouches(bool timeout);
    void cookAndDispatch(nsecs_t when);
    void dispatchVirtualKey(nsecs_t when, uint32_t policyFlags,
            int32_t keyEventAction, int32_t keyEventFlags);

    void dispatchTouches(nsecs_t when, uint32_t policyFlags);
    void dispatchHoverExit(nsecs_t when, uint32_t policyFlags);
    void dispatchHoverEnterAndMove(nsecs_t when, uint32_t policyFlags);
    void dispatchButtonRelease(nsecs_t when, uint32_t policyFlags);
    void dispatchButtonPress(nsecs_t when, uint32_t policyFlags);
    const BitSet32& findActiveIdBits(const CookedPointerData& cookedPointerData);
    void cookPointerData();
    void abortTouches(nsecs_t when, uint32_t policyFlags);

    void dispatchPointerUsage(nsecs_t when, uint32_t policyFlags, PointerUsage pointerUsage);
    void abortPointerUsage(nsecs_t when, uint32_t policyFlags);

    void dispatchPointerGestures(nsecs_t when, uint32_t policyFlags, bool isTimeout);
    void abortPointerGestures(nsecs_t when, uint32_t policyFlags);
    bool preparePointerGestures(nsecs_t when,
            bool* outCancelPreviousGesture, bool* outFinishPreviousGesture,
            bool isTimeout);

    void dispatchPointerStylus(nsecs_t when, uint32_t policyFlags);
    void abortPointerStylus(nsecs_t when, uint32_t policyFlags);

    void dispatchPointerMouse(nsecs_t when, uint32_t policyFlags);
    void abortPointerMouse(nsecs_t when, uint32_t policyFlags);

    void dispatchPointerSimple(nsecs_t when, uint32_t policyFlags,
            bool down, bool hovering);
    void abortPointerSimple(nsecs_t when, uint32_t policyFlags);

    bool assignExternalStylusId(const RawState& state, bool timeout);
    void applyExternalStylusButtonState(nsecs_t when);
    void applyExternalStylusTouchState(nsecs_t when);

    // Dispatches a motion event.
    // If the changedId is >= 0 and the action is POINTER_DOWN or POINTER_UP, the
    // method will take care of setting the index and transmuting the action to DOWN or UP
    // it is the first / last pointer to go down / up.
    void dispatchMotion(nsecs_t when, uint32_t policyFlags, uint32_t source,
            int32_t action, int32_t actionButton,
            int32_t flags, int32_t metaState, int32_t buttonState, int32_t edgeFlags,
            uint32_t deviceTimestamp,
            const PointerProperties* properties, const PointerCoords* coords,
            const uint32_t* idToIndex, BitSet32 idBits,
            int32_t changedId, float xPrecision, float yPrecision, nsecs_t downTime);

    // Updates pointer coords and properties for pointers with specified ids that have moved.
    // Returns true if any of them changed.
    bool updateMovedPointers(const PointerProperties* inProperties,
            const PointerCoords* inCoords, const uint32_t* inIdToIndex,
            PointerProperties* outProperties, PointerCoords* outCoords,
            const uint32_t* outIdToIndex, BitSet32 idBits) const;

    bool isPointInsideSurface(int32_t x, int32_t y);
    const VirtualKey* findVirtualKeyHit(int32_t x, int32_t y);

    static void assignPointerIds(const RawState& last, RawState& current);

    const char* modeToString(DeviceMode deviceMode);
};


class SingleTouchInputMapper : public TouchInputMapper {
public:
    explicit SingleTouchInputMapper(InputDevice* device);
    virtual ~SingleTouchInputMapper();

    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

protected:
    virtual void syncTouch(nsecs_t when, RawState* outState);
    virtual void configureRawPointerAxes();
    virtual bool hasStylus() const;

private:
    SingleTouchMotionAccumulator mSingleTouchMotionAccumulator;
};


class MultiTouchInputMapper : public TouchInputMapper {
public:
    explicit MultiTouchInputMapper(InputDevice* device);
    virtual ~MultiTouchInputMapper();

    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

protected:
    virtual void syncTouch(nsecs_t when, RawState* outState);
    virtual void configureRawPointerAxes();
    virtual bool hasStylus() const;

private:
    MultiTouchMotionAccumulator mMultiTouchMotionAccumulator;

    // Specifies the pointer id bits that are in use, and their associated tracking id.
    BitSet32 mPointerIdBits;
    int32_t mPointerTrackingIdMap[MAX_POINTER_ID + 1];
};

class ExternalStylusInputMapper : public InputMapper {
public:
    explicit ExternalStylusInputMapper(InputDevice* device);
    virtual ~ExternalStylusInputMapper() = default;

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);
    virtual void sync(nsecs_t when);

private:
    SingleTouchMotionAccumulator mSingleTouchMotionAccumulator;
    RawAbsoluteAxisInfo mRawPressureAxis;
    TouchButtonAccumulator mTouchButtonAccumulator;

    StylusState mStylusState;
};


class JoystickInputMapper : public InputMapper {
public:
    explicit JoystickInputMapper(InputDevice* device);
    virtual ~JoystickInputMapper();

    virtual uint32_t getSources();
    virtual void populateDeviceInfo(InputDeviceInfo* deviceInfo);
    virtual void dump(std::string& dump);
    virtual void configure(nsecs_t when, const InputReaderConfiguration* config, uint32_t changes);
    virtual void reset(nsecs_t when);
    virtual void process(const RawEvent* rawEvent);

private:
    struct Axis {
        RawAbsoluteAxisInfo rawAxisInfo;
        AxisInfo axisInfo;

        bool explicitlyMapped; // true if the axis was explicitly assigned an axis id

        float scale;   // scale factor from raw to normalized values
        float offset;  // offset to add after scaling for normalization
        float highScale;  // scale factor from raw to normalized values of high split
        float highOffset; // offset to add after scaling for normalization of high split

        float min;        // normalized inclusive minimum
        float max;        // normalized inclusive maximum
        float flat;       // normalized flat region size
        float fuzz;       // normalized error tolerance
        float resolution; // normalized resolution in units/mm

        float filter;  // filter out small variations of this size
        float currentValue; // current value
        float newValue; // most recent value
        float highCurrentValue; // current value of high split
        float highNewValue; // most recent value of high split

        void initialize(const RawAbsoluteAxisInfo& rawAxisInfo, const AxisInfo& axisInfo,
                bool explicitlyMapped, float scale, float offset,
                float highScale, float highOffset,
                float min, float max, float flat, float fuzz, float resolution) {
            this->rawAxisInfo = rawAxisInfo;
            this->axisInfo = axisInfo;
            this->explicitlyMapped = explicitlyMapped;
            this->scale = scale;
            this->offset = offset;
            this->highScale = highScale;
            this->highOffset = highOffset;
            this->min = min;
            this->max = max;
            this->flat = flat;
            this->fuzz = fuzz;
            this->resolution = resolution;
            this->filter = 0;
            resetValue();
        }

        void resetValue() {
            this->currentValue = 0;
            this->newValue = 0;
            this->highCurrentValue = 0;
            this->highNewValue = 0;
        }
    };

    // Axes indexed by raw ABS_* axis index.
    KeyedVector<int32_t, Axis> mAxes;

    void sync(nsecs_t when, bool force);

    bool haveAxis(int32_t axisId);
    void pruneAxes(bool ignoreExplicitlyMappedAxes);
    bool filterAxes(bool force);

    static bool hasValueChangedSignificantly(float filter,
            float newValue, float currentValue, float min, float max);
    static bool hasMovedNearerToValueWithinFilteredRange(float filter,
            float newValue, float currentValue, float thresholdValue);

    static bool isCenteredAxis(int32_t axis);
    static int32_t getCompatAxis(int32_t axis);

    static void addMotionRange(int32_t axisId, const Axis& axis, InputDeviceInfo* info);
    static void setPointerCoordsAxisValue(PointerCoords* pointerCoords, int32_t axis,
            float value);
};

} // namespace android

#endif // _UI_INPUT_READER_H
