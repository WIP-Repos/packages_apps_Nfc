/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.nfc.cardemulation.util;

import static com.android.nfc.NfcStatsLog.NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__ECP_V1;
import static com.android.nfc.NfcStatsLog.NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__ECP_V2;
import static com.android.nfc.NfcStatsLog.NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__PROPRIETARY_FRAME_UNKNOWN;

import android.annotation.FlaggedApi;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;
import android.os.SystemClock;
import android.sysprop.NfcProperties;
import android.util.Log;

import com.android.nfc.NfcStatsLog;
import com.android.nfc.flags.Flags;

import java.util.HashMap;
import java.util.Objects;

@FlaggedApi(Flags.FLAG_STATSD_CE_EVENTS_FLAG)
public class StatsdUtils {
    static final boolean DBG = NfcProperties.debug_enabled().orElse(true);
    private final String TAG = "StatsdUtils";

    public static final String SE_NAME_HCE = "HCE";
    public static final String SE_NAME_HCEF = "HCEF";

    /** Wrappers for Category values */
    public static final int CE_UNKNOWN =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__UNKNOWN;
    /** Successful cases */
    public static final int CE_HCE_PAYMENT =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__HCE_PAYMENT;
    public static final int CE_HCE_OTHER =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__HCE_OTHER;
    public static final int CE_OFFHOST =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__OFFHOST;
    public static final int CE_OFFHOST_PAYMENT =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__OFFHOST_PAYMENT;
    public static final int CE_OFFHOST_OTHER =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__OFFHOST_OTHER;
    /** NO_ROUTING */
    public static final int CE_NO_ROUTING =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_NO_ROUTING;
    /** WRONG_SETTING */
    public static final int CE_PAYMENT_WRONG_SETTING =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_PAYMENT_WRONG_SETTING;
    public static final int CE_OTHER_WRONG_SETTING =
            NfcStatsLog.NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_OTHER_WRONG_SETTING;
    /** DISCONNECTED_BEFORE_BOUND */
    public static final int CE_PAYMENT_DC_BOUND = NfcStatsLog
            .NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_PAYMENT_DISCONNECTED_BEFORE_BOUND;
    public static final int CE_OTHER_DC_BOUND = NfcStatsLog
            .NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_OTHER_DISCONNECTED_BEFORE_BOUND;
    /** DISCONNECTED_BEFORE_RESPONSE */
    public static final int CE_PAYMENT_DC_RESPONSE = NfcStatsLog
            .NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_PAYMENT_DISCONNECTED_BEFORE_RESPONSE;
    public static final int CE_OTHER_DC_RESPONSE = NfcStatsLog
            .NFC_CARDEMULATION_OCCURRED__CATEGORY__FAILED_HCE_OTHER_DISCONNECTED_BEFORE_RESPONSE;
    /** Wrappers for Category values */

    /** Name of SE terminal to log in statsd */
    private String mSeName = "";
    /** Timestamp in millis when app binding starts */
    private long mBindingStartTimeMillis = 0;
    /** Flag to indicate that the service has not sent the first response */
    private boolean mWaitingForFirstResponse = false;
    /** Current transaction's category to log in statsd */
    private String mTransactionCategory = CardEmulation.EXTRA_CATEGORY;
    /** Current transaction's uid to log in statsd */
    private int mTransactionUid = -1;

    private static final byte FRAME_HEADER_ECP = 0x6A;
    private static final byte FRAME_ECP_V1 = 0x01;
    private static final byte FRAME_ECP_V2 = 0x02;
    private static final int FRAME_ECP_MIN_SIZE = 5;

    private static final int NO_GAIN_INFORMATION = -1;
    private int mLastGainLevel = NO_GAIN_INFORMATION;

    /** Result constants for statsd usage */
    static enum StatsdResult {
        SUCCESS,
        NO_ROUTING_FOR_AID,
        WRONG_APP_AND_DEVICE_SETTINGS,
        DISCONNECTED_BEFORE_BOUND,
        DISCONNECTED_BEFORE_RESPONSE
    }

    public StatsdUtils(String seName) {
        mSeName = seName;

        // HCEF has no category, default it to PAYMENT category to record every call
        if (seName.equals(SE_NAME_HCEF)) mTransactionCategory = CardEmulation.CATEGORY_PAYMENT;
    }

    public StatsdUtils() {}

    private void resetCardEmulationEvent() {
        // Reset mTransactionCategory value to prevent accidental triggers in general
        // except for HCEF, which is always intentional because it only works in foreground
        if (!mSeName.equals(SE_NAME_HCEF)) mTransactionCategory = CardEmulation.EXTRA_CATEGORY;
        mBindingStartTimeMillis = 0;
        mWaitingForFirstResponse = false;
        mTransactionUid = -1;
    }

    private int getCardEmulationStatsdCategory(
            StatsdResult transactionResult, String transactionCategory) {
        switch (transactionResult) {
            case SUCCESS:
                switch (transactionCategory) {
                    case CardEmulation.CATEGORY_PAYMENT:
                        return CE_HCE_PAYMENT;
                    case CardEmulation.CATEGORY_OTHER:
                        return CE_HCE_OTHER;
                    default:
                        return CE_UNKNOWN;
                }

            case NO_ROUTING_FOR_AID:
                return CE_NO_ROUTING;

            case WRONG_APP_AND_DEVICE_SETTINGS:
                switch (transactionCategory) {
                    case CardEmulation.CATEGORY_PAYMENT:
                        return CE_PAYMENT_WRONG_SETTING;
                    case CardEmulation.CATEGORY_OTHER:
                        return CE_OTHER_WRONG_SETTING;
                    default:
                        return CE_UNKNOWN;
                }

            case DISCONNECTED_BEFORE_BOUND:
                switch (transactionCategory) {
                    case CardEmulation.CATEGORY_PAYMENT:
                        return CE_PAYMENT_DC_BOUND;
                    case CardEmulation.CATEGORY_OTHER:
                        return CE_OTHER_DC_BOUND;
                    default:
                        return CE_UNKNOWN;
                }

            case DISCONNECTED_BEFORE_RESPONSE:
                switch (transactionCategory) {
                    case CardEmulation.CATEGORY_PAYMENT:
                        return CE_PAYMENT_DC_RESPONSE;
                    case CardEmulation.CATEGORY_OTHER:
                        return CE_OTHER_DC_RESPONSE;
                    default:
                        return CE_UNKNOWN;
                }
        }
        return CE_UNKNOWN;
    }

    private void logCardEmulationEvent(int statsdCategory) {
        NfcStatsLog.write(
                NfcStatsLog.NFC_CARDEMULATION_OCCURRED, statsdCategory, mSeName, mTransactionUid);
        resetCardEmulationEvent();
    }

    public void logErrorEvent(int errorType, int nciCmd, int ntfStatusCode) {
        NfcStatsLog.write(NfcStatsLog.NFC_ERROR_OCCURRED, errorType, nciCmd, ntfStatusCode);
    }

    public void logErrorEvent(int errorType) {
        logErrorEvent(errorType, 0, 0);
    }

    public void setCardEmulationEventCategory(String category) {
        mTransactionCategory = category;
    }

    public void setCardEmulationEventUid(int uid) {
        mTransactionUid = uid;
    }

    public void notifyCardEmulationEventWaitingForResponse() {
        mWaitingForFirstResponse = true;
    }

    public void notifyCardEmulationEventResponseReceived() {
        mWaitingForFirstResponse = false;
    }

    public void notifyCardEmulationEventWaitingForService() {
        mBindingStartTimeMillis = SystemClock.elapsedRealtime();
    }

    public void notifyCardEmulationEventServiceBound() {
        int bindingLimitMillis = 500;
        if (mBindingStartTimeMillis > 0) {
            long bindingElapsedTimeMillis = SystemClock.elapsedRealtime() - mBindingStartTimeMillis;
            if (DBG) Log.d(TAG, "binding took " + bindingElapsedTimeMillis + " millis");
            if (bindingElapsedTimeMillis >= bindingLimitMillis) {
                logErrorEvent(NfcStatsLog.NFC_ERROR_OCCURRED__TYPE__HCE_LATE_BINDING);
            }
            mBindingStartTimeMillis = 0;
        }
    }

    public void logCardEmulationWrongSettingEvent() {
        int statsdCategory =
                getCardEmulationStatsdCategory(
                        StatsdResult.WRONG_APP_AND_DEVICE_SETTINGS, mTransactionCategory);
        logCardEmulationEvent(statsdCategory);
    }

    public void logCardEmulationNoRoutingEvent() {
        int statsdCategory =
                getCardEmulationStatsdCategory(
                        StatsdResult.NO_ROUTING_FOR_AID, mTransactionCategory);
        logCardEmulationEvent(statsdCategory);
    }

    public void logCardEmulationDeactivatedEvent() {
        if (mTransactionCategory.equals(CardEmulation.EXTRA_CATEGORY)) {
            // Skip deactivation calls without select apdu
            resetCardEmulationEvent();
            return;
        }

        StatsdResult transactionResult;
        if (mBindingStartTimeMillis > 0) {
            transactionResult = StatsdResult.DISCONNECTED_BEFORE_BOUND;
        } else if (mWaitingForFirstResponse) {
            transactionResult = StatsdResult.DISCONNECTED_BEFORE_RESPONSE;
        } else {
            transactionResult = StatsdResult.SUCCESS;
        }
        int statsdCategory =
                getCardEmulationStatsdCategory(transactionResult, mTransactionCategory);
        logCardEmulationEvent(statsdCategory);
    }

    public void logCardEmulationOffhostEvent(String seName) {
        mSeName = seName;

        int statsdCategory;
        switch (mTransactionCategory) {
            case CardEmulation.CATEGORY_PAYMENT:
                statsdCategory = CE_OFFHOST_PAYMENT;
                break;
            case CardEmulation.CATEGORY_OTHER:
                statsdCategory = CE_OFFHOST_OTHER;
                break;
            default:
                statsdCategory = CE_OFFHOST;
        };
        logCardEmulationEvent(statsdCategory);
    }

    public void logFieldChanged(boolean isOn, int fieldStrength) {
        NfcStatsLog.write(NfcStatsLog.NFC_FIELD_CHANGED,
                isOn ? NfcStatsLog.NFC_FIELD_CHANGED__FIELD_STATUS__FIELD_ON
                : NfcStatsLog.NFC_FIELD_CHANGED__FIELD_STATUS__FIELD_OFF, fieldStrength);

        if (!isOn) {
            mLastGainLevel = NO_GAIN_INFORMATION;
        }
    }

    public void logObserveModeStateChanged(boolean enabled, int triggerSource, int latency) {
        NfcStatsLog.write(NfcStatsLog.NFC_OBSERVE_MODE_STATE_CHANGED,
                enabled ? NfcStatsLog.NFC_OBSERVE_MODE_STATE_CHANGED__STATE__OBSERVE_MODE_ENABLED
                        : NfcStatsLog.NFC_OBSERVE_MODE_STATE_CHANGED__STATE__OBSERVE_MODE_DISABLED,
                triggerSource, latency);
    }

    private final HashMap<String, PollingFrameLog> pollingFrameMap = new HashMap<>();

    public void tallyPollingFrame(String frameDataHex, PollingFrame frame) {
        int type = frame.getType();

        int gainLevel = frame.getVendorSpecificGain();
        if (gainLevel != -1) {
            if (mLastGainLevel != gainLevel) {
                logFieldChanged(true, gainLevel);
                mLastGainLevel = gainLevel;
            }
        }

        if (type == PollingFrame.POLLING_LOOP_TYPE_UNKNOWN) {
            byte[] data = frame.getData();

            PollingFrameLog log = pollingFrameMap.getOrDefault(frameDataHex, null);

            if (log == null) {
                PollingFrameLog frameLog = new PollingFrameLog(data);

                pollingFrameMap.put(frameDataHex, frameLog);
            } else {
                log.repeatCount++;
            }
        }
    }

    public void logPollingFrames() {
        for (PollingFrameLog log : pollingFrameMap.values()) {
            writeToStatsd(log);
        }
        pollingFrameMap.clear();
    }

    protected static int getFrameType(byte[] data) {
        int frameType =
          NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__PROPRIETARY_FRAME_UNKNOWN;

        if (data != null && data.length >= FRAME_ECP_MIN_SIZE && data[0] == FRAME_HEADER_ECP) {
            frameType = switch (data[1]) {
                case FRAME_ECP_V1 ->
                        NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__ECP_V1;
                case FRAME_ECP_V2 ->
                        NFC_POLLING_LOOP_NOTIFICATION_REPORTED__PROPRIETARY_FRAME_TYPE__ECP_V2;
                default -> frameType;
            };
        }
        return frameType;
    }

    protected void writeToStatsd(PollingFrameLog frameLog) {
        NfcStatsLog.write(NfcStatsLog.NFC_POLLING_LOOP_NOTIFICATION_REPORTED,
                frameLog.frameType,
                frameLog.repeatCount);
    }

    protected static class PollingFrameLog {
        int repeatCount = 1;
        final int frameType;

        public PollingFrameLog(byte[] data) {
            frameType = getFrameType(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PollingFrameLog)) return false;
            PollingFrameLog that = (PollingFrameLog) o;
            return repeatCount == that.repeatCount && frameType == that.frameType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(repeatCount, frameType);
        }

        @Override
        public String toString() {
            return "PollingFrameLog{" +
                    "repeatCount=" + repeatCount +
                    ", frameType=" + frameType +
                    '}';
        }
    }
}
