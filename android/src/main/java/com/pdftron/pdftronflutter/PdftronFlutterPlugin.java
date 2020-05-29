package com.pdftron.pdftronflutter;

import android.content.Context;
import android.net.Uri;

import com.pdftron.common.PDFNetException;
import com.pdftron.pdf.PDFNet;
import com.pdftron.pdf.config.ViewerConfig;
import com.pdftron.pdf.controls.DocumentActivity;
import com.pdftron.pdftronflutter.factories.DocumentViewFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static com.pdftron.pdftronflutter.PluginUtils.disabledElements;
import static com.pdftron.pdftronflutter.PluginUtils.disabledTools;

/**
 * PdftronFlutterPlugin
 */
public class PdftronFlutterPlugin implements MethodCallHandler {
    private final Context mContext;

    public PdftronFlutterPlugin(Context context) {
        mContext = context;
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "pdftron_flutter");
        channel.setMethodCallHandler(new PdftronFlutterPlugin(registrar.activeContext()));


        registrar.platformViewRegistry().registerViewFactory("pdftron_flutter/documentview", new DocumentViewFactory(registrar.messenger(), registrar.activeContext()));
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "getVersion":
                try {
                    String pdftronVersion = Double.toString(PDFNet.getVersion());
                    result.success(pdftronVersion);
                } catch (PDFNetException e) {
                    e.printStackTrace();
                    result.error(Long.toString(e.getErrorCode()), "PDFTronException Error: " + e, null);
                }
                break;
            case "initialize":
                try {
                    String licenseKey = call.argument("licenseKey");
                    com.pdftron.pdf.utils.AppUtils.initializePDFNetApplication(mContext.getApplicationContext(), licenseKey);
                } catch (PDFNetException e) {
                    e.printStackTrace();
                    result.error(Long.toString(e.getErrorCode()), "PDFTronException Error: " + e, null);
                }
                break;
            case "openDocument":
                String document = call.argument("document");
                String password = call.argument("password");
                String config = call.argument("config");
                openDocument(document, password, config);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private static final String disabledElements = "disabledElements";
    private static final String disabledTools = "disabledTools";
    private static final String multiTabEnabled = "multiTabEnabled";
    private static final String customHeaders = "customHeaders";

    private ArrayList<ToolManager.ToolMode> mDisabledTools = new ArrayList<>();

    private void openDocument(String document, String password, String configStr) {
        ViewerConfig.Builder builder = new ViewerConfig.Builder()
                .multiTabEnabled(false)
                .openUrlCachePath(mContext.getCacheDir().getAbsolutePath());

        ToolManagerBuilder toolManagerBuilder = ToolManagerBuilder.from();

        JSONObject customHeaderJson = null;
        if (configStr != null && !configStr.equals("null")) {
            try {
                JSONObject configJson = new JSONObject(configStr);
                if (!configJson.isNull(disabledElements)) {
                    JSONArray array = configJson.getJSONArray(disabledElements);
                    disableElements(builder, array);
                    builder = PluginUtils.disableElements(builder, array);
                }
                if (!configJson.isNull(disabledTools)) {
                    JSONArray array = configJson.getJSONArray(disabledTools);
                    disableTools(builder, array);
                }
                if (!configJson.isNull(multiTabEnabled)) {
                    boolean val = configJson.getBoolean(multiTabEnabled);
                    builder = builder.multiTabEnabled(val);
                }
                if (!configJson.isNull(customHeaders)) {
                    customHeaderJson = configJson.getJSONObject(customHeaders);
                    builder = PluginUtils.disableTools(builder, array);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (mDisabledTools.size() > 0) {
            ToolManager.ToolMode[] modes = mDisabledTools.toArray(new ToolManager.ToolMode[0]);
            if (modes.length > 0) {
                toolManagerBuilder = toolManagerBuilder.disableToolModes(modes);
            }
        }

        builder = builder.toolManagerBuilder(toolManagerBuilder);

        final Uri fileLink = Uri.parse(document);
        DocumentActivity.openDocument(mContext, fileLink, password, customHeaderJson, builder.build());
    }

    private void disableElements(ViewerConfig.Builder builder, JSONArray args) throws JSONException {
        for (int i = 0; i < args.length(); i++) {
            String item = args.getString(i);
            if ("toolsButton".equals(item)) {
                builder = builder.showAnnotationToolbarOption(false);
            } else if ("searchButton".equals(item)) {
                builder = builder.showSearchView(false);
            } else if ("shareButton".equals(item)) {
                builder = builder.showShareOption(false);
            } else if ("viewControlsButton".equals(item)) {
                builder = builder.showDocumentSettingsOption(false);
            } else if ("thumbnailsButton".equals(item)) {
                builder = builder.showThumbnailView(false);
            } else if ("listsButton".equals(item)) {
                builder = builder
                        .showAnnotationsList(false)
                        .showOutlineList(false)
                        .showUserBookmarksList(false);
            } else if ("thumbnailSlider".equals(item)) {
                builder = builder.showBottomNavBar(false);
            } else if ("saveCopyButton".equals(item)) {
                builder = builder.showSaveCopyOption(false);
            } else if ("editPagesButton".equals(item)) {
                builder = builder.showEditPagesOption(false);
            } else if ("printButton".equals(item)) {
                builder = builder.showPrintOption(false);
            } else if ("fillAndSignButton".equals(item)) {
                builder = builder.showFillAndSignToolbarOption(false);
            } else if ("prepareFormButton".equals(item)) {
                builder = builder.showFormToolbarOption(false);
            } else if ("reflowModeButton".equals(item)) {
                builder = builder.showReflowOption(false);
            }
        }
        disableTools(builder, args);
    }

    private void disableTools(ViewerConfig.Builder builder, JSONArray args) throws JSONException {
        for (int i = 0; i < args.length(); i++) {
            String item = args.getString(i);
            ToolManager.ToolMode mode = convStringToToolMode(item);
            if (mode != null) {
                mDisabledTools.add(mode);
            }
        }
    }

    private ToolManager.ToolMode convStringToToolMode(String item) {
        ToolManager.ToolMode mode = null;
        if ("freeHandToolButton".equals(item) || "AnnotationCreateFreeHand".equals(item)) {
            mode = ToolManager.ToolMode.INK_CREATE;
        } else if ("highlightToolButton".equals(item) || "AnnotationCreateTextHighlight".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_HIGHLIGHT;
        } else if ("underlineToolButton".equals(item) || "AnnotationCreateTextUnderline".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_UNDERLINE;
        } else if ("squigglyToolButton".equals(item) || "AnnotationCreateTextSquiggly".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_SQUIGGLY;
        } else if ("strikeoutToolButton".equals(item) || "AnnotationCreateTextStrikeout".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_STRIKEOUT;
        } else if ("rectangleToolButton".equals(item) || "AnnotationCreateRectangle".equals(item)) {
            mode = ToolManager.ToolMode.RECT_CREATE;
        } else if ("ellipseToolButton".equals(item) || "AnnotationCreateEllipse".equals(item)) {
            mode = ToolManager.ToolMode.OVAL_CREATE;
        } else if ("lineToolButton".equals(item) || "AnnotationCreateLine".equals(item)) {
            mode = ToolManager.ToolMode.LINE_CREATE;
        } else if ("arrowToolButton".equals(item) || "AnnotationCreateArrow".equals(item)) {
            mode = ToolManager.ToolMode.ARROW_CREATE;
        } else if ("polylineToolButton".equals(item) || "AnnotationCreatePolyline".equals(item)) {
            mode = ToolManager.ToolMode.POLYLINE_CREATE;
        } else if ("polygonToolButton".equals(item) || "AnnotationCreatePolygon".equals(item)) {
            mode = ToolManager.ToolMode.POLYGON_CREATE;
        } else if ("cloudToolButton".equals(item) || "AnnotationCreatePolygonCloud".equals(item)) {
            mode = ToolManager.ToolMode.CLOUD_CREATE;
        } else if ("signatureToolButton".equals(item) || "AnnotationCreateSignature".equals(item)) {
            mode = ToolManager.ToolMode.SIGNATURE;
        } else if ("freeTextToolButton".equals(item) || "AnnotationCreateFreeText".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_CREATE;
        } else if ("stickyToolButton".equals(item) || "AnnotationCreateSticky".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_ANNOT_CREATE;
        } else if ("calloutToolButton".equals(item) || "AnnotationCreateCallout".equals(item)) {
            mode = ToolManager.ToolMode.CALLOUT_CREATE;
        } else if ("stampToolButton".equals(item) || "AnnotationCreateStamp".equals(item)) {
            mode = ToolManager.ToolMode.STAMPER;
        } else if ("AnnotationCreateDistanceMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.RULER_CREATE;
        } else if ("AnnotationCreatePerimeterMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.PERIMETER_MEASURE_CREATE;
        } else if ("AnnotationCreateAreaMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.AREA_MEASURE_CREATE;
        } else if ("TextSelect".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_SELECT;
        } else if ("AnnotationEdit".equals(item)) {
            mode = ToolManager.ToolMode.ANNOT_EDIT_RECT_GROUP;
        } else if ("AnnotationCreateSound".equals(item)) {
            mode = ToolManager.ToolMode.SOUND_CREATE;
        } else if ("AnnotationCreateFreeHighlighter".equals(item)) {
            mode = ToolManager.ToolMode.FREE_HIGHLIGHTER;
        } else if ("AnnotationCreateRubberStamp".equals(item)) {
            mode = ToolManager.ToolMode.RUBBER_STAMPER;
        } else if ("Eraser".equals(item)) {
            mode = ToolManager.ToolMode.INK_ERASER;
        }
        return mode;
    }
}
