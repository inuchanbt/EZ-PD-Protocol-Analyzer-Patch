package com.cypress.ezpdanalyzer.ui.views;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;

public final class AdvancedFeatureUsbSync {

    /*
     * Buttons whose enabled state follows the physical
     * EZ-PD Analyzer USB attach/detach state.
     *
     * Terminations Clear is deliberately NOT managed here.
     * The original Utility keeps Clear enabled at all times.
     */
    private static Button triggerSetButton;
    private static Button terminationSetButton;

    /*
     * True once our UsbServicesListener has been installed.
     */
    private static boolean installed = false;

    /*
     * Keep a strong reference to the dynamic proxy.
     *
     * Without this reference the proxy could theoretically
     * become eligible for garbage collection.
     */
    private static Object usbServicesListenerProxy;

    private AdvancedFeatureUsbSync() {
        /*
         * Utility class.
         */
    }


    /*
     * Register the Trigger Set button.
     */
    public static synchronized void registerTrigger(
            Button setButton) {

        triggerSetButton = setButton;

        ensureInstalled();
    }


    /*
     * Register the Terminations Set button.
     *
     * Clear is intentionally not supplied here.
     */
    public static synchronized void registerTerminations(
            Button setButton) {

        terminationSetButton = setButton;

        ensureInstalled();
    }


    /*
     * Install one additional javax.usb UsbServicesListener.
     *
     * This is event driven:
     *
     *   USB attach -> callback once
     *   USB detach -> callback once
     *
     * There is:
     *
     *   - no timer
     *   - no polling
     *   - no periodic device enumeration
     *   - no CMD_CHECK_DEVICE traffic
     */
    private static synchronized void ensureInstalled() {

        if (installed) {
            return;
        }

        try {

            /*
             * Resolve javax.usb classes via reflection so this
             * source file has no compile-time javax.usb dependency.
             */
            final Class<?> listenerClass =
                Class.forName(
                    "javax.usb.event.UsbServicesListener"
                );

            Class<?> hostManagerClass =
                Class.forName(
                    "javax.usb.UsbHostManager"
                );

            Method getUsbServices =
                hostManagerClass.getMethod(
                    "getUsbServices"
                );

            final Object usbServices =
                getUsbServices.invoke(null);


            /*
             * Create our UsbServicesListener dynamically.
             */
            InvocationHandler handler =
                new InvocationHandler() {

                    @Override
                    public Object invoke(
                            Object proxy,
                            Method method,
                            Object[] args)
                            throws Throwable {

                        String name =
                            method.getName();


                        /*
                         * Physical USB attach event.
                         */
                        if ("usbDeviceAttached".equals(name)) {

                            if (args != null &&
                                args.length == 1 &&
                                isAnalyzerEvent(args[0])) {

                                /*
                                 * The attach event itself is
                                 * authoritative.
                                 *
                                 * Do NOT call loadDeviceSelection()
                                 * here.
                                 */
                                updateButtonsAsync(true);
                            }

                            return null;
                        }


                        /*
                         * Physical USB detach event.
                         */
                        if ("usbDeviceDetached".equals(name)) {

                            if (args != null &&
                                args.length == 1 &&
                                isAnalyzerEvent(args[0])) {

                                /*
                                 * The detach event itself is
                                 * authoritative.
                                 *
                                 * This is important because
                                 * EZPDUtil.loadDeviceSelection()
                                 * can retain a stale UsbDevice
                                 * after physical removal.
                                 */
                                updateButtonsAsync(false);
                            }

                            return null;
                        }


                        /*
                         * Basic Object methods that may be called
                         * on the dynamic proxy.
                         */
                        if ("toString".equals(name)) {

                            return
                                "AdvancedFeatureUsbSyncListener";
                        }

                        if ("hashCode".equals(name)) {

                            return Integer.valueOf(
                                System.identityHashCode(
                                    proxy
                                )
                            );
                        }

                        if ("equals".equals(name)) {

                            return Boolean.valueOf(
                                args != null &&
                                args.length == 1 &&
                                proxy == args[0]
                            );
                        }


                        return null;
                    }
                };


            /*
             * Build the UsbServicesListener proxy.
             */
            usbServicesListenerProxy =
                Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[] {
                        listenerClass
                    },
                    handler
                );


            /*
             * UsbServices.addUsbServicesListener(...)
             */
            Class<?> servicesClass =
                Class.forName(
                    "javax.usb.UsbServices"
                );

            Method addListener =
                servicesClass.getMethod(
                    "addUsbServicesListener",
                    listenerClass
                );

            addListener.invoke(
                usbServices,
                usbServicesListenerProxy
            );


            installed = true;

        } catch (Throwable t) {

            /*
             * Deliberately NO polling fallback.
             *
             * If event-hook installation fails, the original
             * View initialization behavior remains available.
             */
            System.err.println(
                "[AdvancedFeatureUsbSync] " +
                "USB event hook unavailable: " +
                t
            );
        }
    }


    /*
     * Return true only when the USB event belongs to one of
     * the supported CY4500-family EZ-PD Protocol Analyzer devices.
     *
     * Supported Cypress/Infineon VID/PIDs:
     *
     *   04B4:0078  original CY4500
     *   04B4:F67E  newer Analyzer
     *   04B4:FDEF  CY4500-EPR
     */
    private static boolean isAnalyzerEvent(
            Object usbServicesEvent) {

        try {

            Class<?> eventClass =
                Class.forName(
                    "javax.usb.event.UsbServicesEvent"
                );

            Method getUsbDevice =
                eventClass.getMethod(
                    "getUsbDevice"
                );

            Object device =
                getUsbDevice.invoke(
                    usbServicesEvent
                );

            if (device == null) {
                return false;
            }


            Class<?> usbDeviceClass =
                Class.forName(
                    "javax.usb.UsbDevice"
                );

            Method getDescriptor =
                usbDeviceClass.getMethod(
                    "getUsbDeviceDescriptor"
                );

            Object descriptor =
                getDescriptor.invoke(
                    device
                );

            if (descriptor == null) {
                return false;
            }


            Class<?> descriptorClass =
                Class.forName(
                    "javax.usb.UsbDeviceDescriptor"
                );

            Method idVendor =
                descriptorClass.getMethod(
                    "idVendor"
                );

            Method idProduct =
                descriptorClass.getMethod(
                    "idProduct"
                );


            int vid =
                ((Number)
                    idVendor.invoke(descriptor))
                    .intValue()
                    & 0xFFFF;

            int pid =
                ((Number)
                    idProduct.invoke(descriptor))
                    .intValue()
                    & 0xFFFF;


            if (vid != 0x04B4) {
                return false;
            }


            return
                pid == 0x0078 ||
                pid == 0xF67E ||
                pid == 0xFDEF;

        } catch (Throwable t) {

            /*
             * An unrelated or malformed USB event must never
             * affect the UI.
             */
            return false;
        }
    }


    /*
     * Update the two Set buttons on the SWT UI thread.
     *
     * This method is called ONLY in response to a physical
     * Analyzer attach/detach event.
     *
     * Terminations Clear is deliberately NOT touched.
     */
    private static void updateButtonsAsync(
            final boolean connected) {

        Display display =
            Display.getDefault();

        if (display == null ||
            display.isDisposed()) {

            return;
        }


        display.asyncExec(
            new Runnable() {

                @Override
                public void run() {

                    /*
                     * Trigger Set
                     */
                    setEnabled(
                        triggerSetButton,
                        connected
                    );


                    /*
                     * Terminations Set
                     */
                    setEnabled(
                        terminationSetButton,
                        connected
                    );


                    /*
                     * Terminations Clear:
                     *
                     * intentionally untouched.
                     *
                     * Original Utility behavior is to leave
                     * Clear enabled at all times.
                     */
                }
            }
        );
    }


    /*
     * Safely change a Button's enabled state.
     */
    private static void setEnabled(
            Button button,
            boolean enabled) {

        if (button != null &&
            !button.isDisposed()) {

            button.setEnabled(
                enabled
            );
        }
    }
}
