/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <stdint.h>
#include <sys/types.h>

#include <binder/PermissionCache.h>
#include <binder/IPCThreadState.h>

#include <private/android_filesystem_config.h>

#include "Client.h"
#include "Layer.h"
#include "SurfaceFlinger.h"

namespace android {

// ---------------------------------------------------------------------------

const String16 sAccessSurfaceFlinger("android.permission.ACCESS_SURFACE_FLINGER");

// ---------------------------------------------------------------------------

Client::Client(const sp<SurfaceFlinger>& flinger)
    : Client(flinger, nullptr)
{
}

Client::Client(const sp<SurfaceFlinger>& flinger, const sp<Layer>& parentLayer)
    : mFlinger(flinger),
      mParentLayer(parentLayer)
{
}

Client::~Client()
{
    // We need to post a message to remove our remaining layers rather than
    // do so directly by acquiring the SurfaceFlinger lock. If we were to
    // attempt to directly call the lock it becomes effectively impossible
    // to use sp<Client> while holding the SF lock as descoping it could
    // then trigger a dead-lock.

    const size_t count = mLayers.size();
    for (size_t i=0 ; i<count ; i++) {
        sp<Layer> l = mLayers.valueAt(i).promote();
        if (l == nullptr) {
            continue;
        }
        mFlinger->postMessageAsync(new LambdaMessage([flinger = mFlinger, l]() {
            flinger->removeLayer(l);
        }));
    }
}

void Client::updateParent(const sp<Layer>& parentLayer) {
    Mutex::Autolock _l(mLock);

    // If we didn't ever have a parent, then we must instead be
    // relying on permissions and we never need a parent.
    if (mParentLayer != nullptr) {
        mParentLayer = parentLayer;
    }
}

sp<Layer> Client::getParentLayer(bool* outParentDied) const {
    Mutex::Autolock _l(mLock);
    sp<Layer> parent = mParentLayer.promote();
    if (outParentDied != nullptr) {
        *outParentDied = (mParentLayer != nullptr && parent == nullptr);
    }
    return parent;
}

status_t Client::initCheck() const {
    return NO_ERROR;
}

void Client::attachLayer(const sp<IBinder>& handle, const sp<Layer>& layer)
{
    Mutex::Autolock _l(mLock);
    mLayers.add(handle, layer);
}

void Client::detachLayer(const Layer* layer)
{
    Mutex::Autolock _l(mLock);
    // we do a linear search here, because this doesn't happen often
    const size_t count = mLayers.size();
    for (size_t i=0 ; i<count ; i++) {
        if (mLayers.valueAt(i) == layer) {
            mLayers.removeItemsAt(i, 1);
            break;
        }
    }
}

bool Client::isAttached(const sp<IBinder>& handle) const
{
    Mutex::Autolock _l(mLock);
    sp<Layer> lbc;
    wp<Layer> layer(mLayers.valueFor(handle));
    if (layer != 0) {
        return true;
    }
    return false;
}

status_t Client::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    // these must be checked
     IPCThreadState* ipc = IPCThreadState::self();
     const int pid = ipc->getCallingPid();
     const int uid = ipc->getCallingUid();
     const int self_pid = getpid();
     // If we are called from another non root process without the GRAPHICS, SYSTEM, or ROOT
     // uid we require the sAccessSurfaceFlinger permission.
     // We grant an exception in the case that the Client has a "parent layer", as its
     // effects will be scoped to that layer.
     if (CC_UNLIKELY(pid != self_pid && uid != AID_GRAPHICS && uid != AID_SYSTEM && uid != 0)
             && (getParentLayer() == nullptr)) {
         // we're called from a different process, do the real check
         if (!PermissionCache::checkCallingPermission(sAccessSurfaceFlinger))
         {
             ALOGE("Permission Denial: "
                     "can't openGlobalTransaction pid=%d, uid<=%d", pid, uid);
             // only warning not return (CPH)
         }
     }
     return BnSurfaceComposerClient::onTransact(code, data, reply, flags);
}

status_t Client::createSurface(
        const String8& name,
        uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
        const sp<IBinder>& parentHandle, int32_t windowType, int32_t ownerUid,
        sp<IBinder>* handle,
        sp<IGraphicBufferProducer>* gbp)
{
    bool parentDied;
    sp<Layer> parentLayer;
    if (!parentHandle) parentLayer = getParentLayer(&parentDied);
    if (parentHandle == nullptr && parentDied) {
        return NAME_NOT_FOUND;
    }

    /*
     * createSurface must be called from the GL thread so that it can
     * have access to the GL context.
     */
    class MessageCreateLayer : public MessageBase {
        SurfaceFlinger* flinger;
        Client* client;
        sp<IBinder>* handle;
        sp<IGraphicBufferProducer>* gbp;
        status_t result;
        const String8& name;
        uint32_t w, h;
        PixelFormat format;
        uint32_t flags;
        const sp<IBinder>& parentHandle;
        const sp<Layer>& parentLayer;
        int32_t windowType;
        int32_t ownerUid;
    public:
        MessageCreateLayer(SurfaceFlinger* flinger,
                const String8& name, Client* client,
                uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
                sp<IBinder>* handle, int32_t windowType, int32_t ownerUid,
                sp<IGraphicBufferProducer>* gbp,
                const sp<IBinder>& parentHandle,
                const sp<Layer>& parentLayer)
            : flinger(flinger), client(client),
              handle(handle), gbp(gbp), result(NO_ERROR),
              name(name), w(w), h(h), format(format), flags(flags),
              parentHandle(parentHandle), parentLayer(parentLayer),
              windowType(windowType), ownerUid(ownerUid) {
        }
        status_t getResult() const { return result; }
        virtual bool handler() {
            result = flinger->createLayer(name, client, w, h, format, flags,
                    windowType, ownerUid, handle, gbp, parentHandle, parentLayer);
            return true;
        }
    };

    sp<MessageBase> msg = new MessageCreateLayer(mFlinger.get(),
            name, this, w, h, format, flags, handle,
            windowType, ownerUid, gbp, parentHandle, parentLayer);
    mFlinger->postMessageSync(msg);
    return static_cast<MessageCreateLayer*>( msg.get() )->getResult();
}

status_t Client::destroySurface(const sp<IBinder>& handle) {
    return mFlinger->onLayerRemoved(this, handle);
}

status_t Client::clearLayerFrameStats(const sp<IBinder>& handle) const {
    return mFlinger->clearLayerFrameStats(this, handle);
}

status_t Client::getLayerFrameStats(const sp<IBinder>& handle, FrameStats* outStats) const {
    return mFlinger->getLayerFrameStats(this, handle, outStats);
}

// ---------------------------------------------------------------------------
}; // namespace android
