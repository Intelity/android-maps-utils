package com.google.maps.android.kml;


import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbsKmlRenderer {

  private static final String LOG_TAG = "KmlRenderer";

  private final HashSet<String> mMarkerIconUrls;

  private final ArrayList<String> mGroundOverlayUrls;

  private GoogleMap mMap;

  private HashMap<KmlPlacemark, Object> mPlacemarks;

  private HashMap<String, String> mStyleMaps;

  private ArrayList<KmlContainer> mContainers;

  private HashMap<String, KmlStyle> mStyles;

  private HashMap<String, KmlStyle> mStylesRenderer;

  private HashMap<KmlGroundOverlay, GroundOverlay> mGroundOverlays;

  private boolean mLayerVisible;

  private boolean mMarkerIconsDownloaded;

  private boolean mGroundOverlayImagesDownloaded;

  private Context mContext;

  public AbsKmlRenderer(GoogleMap map, Context context) {
    mContext = context;
    mMap = map;
    mMarkerIconUrls = new HashSet<>();
    mGroundOverlayUrls = new ArrayList<String>();
    mStylesRenderer = new HashMap<String, KmlStyle>();
    mLayerVisible = false;
    mMarkerIconsDownloaded = false;
    mGroundOverlayImagesDownloaded = false;
  }

  public boolean isLayerVisible() {
    return mLayerVisible;
  }

  public Context getContext() {
    return mContext;
  }

  /**
   * Gets the visibility of the placemark if it is specified. A visibility value of "1"
   * corresponds as "true", a visibility value of "0" corresponds as false. If the
   * visibility is not set, the method returns "true".
   *
   * @param placemark Placemark to obtain visibility from.
   * @return False if a Placemark has a visibility value of "1", true otherwise.
   */
  private static boolean getPlacemarkVisibility(KmlPlacemark placemark) {
    boolean isPlacemarkVisible = true;
    if (placemark.hasProperty("visibility")) {
      String placemarkVisibility = placemark.getProperty("visibility");
      if (Integer.parseInt(placemarkVisibility) == 0) {
        isPlacemarkVisible = false;
      }
    }
    return isPlacemarkVisible;
  }

  /**
   * Scales a Bitmap to a specified float.
   *
   * @param unscaledBitmap Unscaled bitmap image to scale.
   * @param scale Scale value. A "1.0" scale value corresponds to the original size of the Bitmap
   * @return A scaled bitmap image
   */
  protected Bitmap scaleIcon(Context mContext, Bitmap unscaledBitmap, Double scale) {
    int width = (int) (unscaledBitmap.getWidth() * scale);
    int height = (int) (unscaledBitmap.getHeight() * scale);
    return Bitmap.createScaledBitmap(unscaledBitmap, width, height, false);
  }

  /**
   * Removes all given KML placemarks from the map and clears all stored placemarks.
   *
   * @param placemarks placemarks to remove
   */
  private static void removePlacemarks(HashMap<KmlPlacemark, Object> placemarks) {
    // Remove map object from the map
    for (Object mapObject : placemarks.values()) {
      if (mapObject instanceof Marker) {
        ((Marker) mapObject).remove();
      } else if (mapObject instanceof Polyline) {
        ((Polyline) mapObject).remove();
      } else if (mapObject instanceof Polygon) {
        ((Polygon) mapObject).remove();
      }
    }
  }

  /**
   * Gets the visibility of the container
   *
   * @param kmlContainer             container to check visibility of
   * @param isParentContainerVisible true if the parent container is visible, false otherwise
   * @return true if this container is visible, false otherwise
   */
    /*package*/
  static boolean getContainerVisibility(KmlContainer kmlContainer, boolean
      isParentContainerVisible) {
    if (isParentContainerVisible) {
      boolean isChildContainerVisible = true;
      if (kmlContainer.hasProperty("visibility")) {
        String containerVisibility = kmlContainer.getProperty("visibility");
        if (Integer.parseInt(containerVisibility) == 0) {
          isChildContainerVisible = false;
        }
      }
      return isChildContainerVisible;
    }
    return false;
  }

  /**
   * Removes all ground overlays in the given hashmap
   *
   * @param groundOverlays hashmap of ground overlays to remove
   */
  private void removeGroundOverlays(HashMap<KmlGroundOverlay, GroundOverlay> groundOverlays) {
    for (GroundOverlay groundOverlay : groundOverlays.values()) {
      groundOverlay.remove();
    }
  }

  /**
   * Removes all the KML data from the map and clears all the stored placemarks of those which
   * are in a container.
   */
  private void removeContainers(Iterable<KmlContainer> containers) {
    for (KmlContainer container : containers) {
      removePlacemarks(mPlacemarks);
      removeGroundOverlays(container.getGroundOverlayHashMap());
      removeContainers(container.getContainers());
    }
  }

  /**
   * Iterates a list of styles and assigns a style
   */
    /*package*/ void assignStyleMap(HashMap<String, String> styleMap,
                                    HashMap<String, KmlStyle> styles) {
    for (String styleMapKey : styleMap.keySet()) {
      String styleMapValue = styleMap.get(styleMapKey);
      if (styles.containsKey(styleMapValue)) {
        styles.put(styleMapKey, styles.get(styleMapValue));
      }
    }
  }

  /**
   * Stores all given data and adds it onto the map
   *
   * @param styles         hashmap of styles
   * @param styleMaps      hashmap of style maps
   * @param placemarks     hashmap of placemarks
   * @param folders        array of containers
   * @param groundOverlays hashmap of ground overlays
   */
    /* package */ void storeKmlData(HashMap<String, KmlStyle> styles,
                                    HashMap<String, String> styleMaps,
                                    HashMap<KmlPlacemark, Object> placemarks, ArrayList<KmlContainer> folders,
                                    HashMap<KmlGroundOverlay, GroundOverlay> groundOverlays) {
    mStyles = styles;
    mStyleMaps = styleMaps;
    mPlacemarks = placemarks;
    mContainers = folders;
    mGroundOverlays = groundOverlays;
  }

  protected void addLayerToMap() {
    mStylesRenderer.putAll(mStyles);
    assignStyleMap(mStyleMaps, mStylesRenderer);
    addGroundOverlays(mGroundOverlays, mContainers);
    addContainerGroupToMap(mContainers, true);
    addPlacemarksToMap(mPlacemarks);
    if (!mGroundOverlayImagesDownloaded) {
      downloadGroundOverlays();
    }

    mLayerVisible = true;

    if (!mMarkerIconsDownloaded || shouldForceDownload()) {
      downloadMarkerIcons();
    }
  }

  protected boolean shouldForceDownload() {
    return false;
  }

  /**
   * Gets the map that objects are being placed on
   *
   * @return map
   */
  protected GoogleMap getMap() {
    return mMap;
  }

  /**
   * Sets the map that objects are being placed on
   *
   * @param map map to place placemark, container, style and ground overlays on
   */
  protected void setMap(GoogleMap map) {
    removeLayerFromMap();
    mMap = map;
    addLayerToMap();
  }

  public HashMap<KmlPlacemark, Object> getPlacemarks() {
    return mPlacemarks;
  }

  /**
   * Checks if the layer contains placemarks
   *
   * @return true if there are placemarks, false otherwise
   */
    /* package */ boolean hasKmlPlacemarks() {
    return mPlacemarks != null && mPlacemarks.size() > 0;
  }

  /**
   * Gets an iterable of KmlPlacemark objects
   *
   * @return iterable of KmlPlacemark objects
   */
    /* package */ Iterable<KmlPlacemark> getKmlPlacemarks() {
    return mPlacemarks.keySet();
  }

  /**
   * Checks if the layer contains any KmlContainers
   *
   * @return true if there is at least 1 container within the KmlLayer, false otherwise
   */
    /* package */ boolean hasNestedContainers() {
    return mContainers != null && mContainers.size() > 0;
  }

  /**
   * Gets an iterable of KmlContainerInterface objects
   *
   * @return iterable of KmlContainerInterface objects
   */
    /* package */ Iterable<KmlContainer> getNestedContainers() {
    return mContainers;
  }

  /**
   * Gets an iterable of KmlGroundOverlay objects
   *
   * @return iterable of KmlGroundOverlay objects
   */
    /* package */ Iterable<KmlGroundOverlay> getGroundOverlays() {
    return mGroundOverlays.keySet();
  }

  /**
   * Removes all the KML data from the map and clears all the stored placemarks
   */
  protected void removeLayerFromMap() {
    removePlacemarks(mPlacemarks);
    removeGroundOverlays(mGroundOverlays);
    if (hasNestedContainers()) {
      removeContainers(getNestedContainers());
    }
    mLayerVisible = false;
    mStylesRenderer.clear();
  }

  /**
   * Iterates over the placemarks, gets its style or assigns a default one and adds it to the map
   */
  private void addPlacemarksToMap(HashMap<KmlPlacemark, Object> placemarks) {
    for (KmlPlacemark kmlPlacemark : placemarks.keySet()) {
      boolean isPlacemarkVisible = getPlacemarkVisibility(kmlPlacemark);
      Object mapObject = addPlacemarkToMap(kmlPlacemark, isPlacemarkVisible);
      // Placemark stores a KmlPlacemark as a key, and GoogleMap Object as its value
      placemarks.put(kmlPlacemark, mapObject);
    }
  }

  /**
   * Combines style and visibility to apply to a placemark geometry object and adds it to the map
   *
   * @param placemark           Placemark to obtain geometry object to add to the map
   * @param placemarkVisibility boolean value, where true indicates the placemark geometry is
   *                            shown initially on the map, false for not shown initially on the
   *                            map.
   * @return Google Map Object of the placemark geometry after it has been added to the map.
   */
  private Object addPlacemarkToMap(KmlPlacemark placemark, boolean placemarkVisibility) {
    //If the placemark contains a geometry, then we add it to the map
    //If it doesnt contain a geometry, we do not add anything to the map and just store values
    if (placemark.getGeometry() != null) {
      String placemarkId = placemark.getStyleId();
      KmlGeometry geometry = placemark.getGeometry();
      KmlStyle style = getPlacemarkStyle(placemarkId);
      KmlStyle inlineStyle = placemark.getInlineStyle();
      return addToMap(placemark, geometry, style, inlineStyle, placemarkVisibility);
    }
    return null;
  }

  /**
   * Adds placemarks with their corresponding styles onto the map
   *
   * @param kmlContainers An arraylist of folders
   */
  private void addContainerGroupToMap(Iterable<KmlContainer> kmlContainers,
                                      boolean containerVisibility) {
    for (KmlContainer container : kmlContainers) {
      boolean isContainerVisible = getContainerVisibility(container, containerVisibility);
      if (container.getStyles() != null) {
        // Stores all found styles from the container
        mStylesRenderer.putAll(container.getStyles());
      }
      if (container.getStyleMap() != null) {
        // Stores all found style maps from the container
        assignStyleMap(container.getStyleMap(), mStylesRenderer);
      }
      addContainerObjectToMap(container, isContainerVisible);
      if (container.hasContainers()) {
        addContainerGroupToMap(container.getContainers(), isContainerVisible);
      }
    }
  }

  /**
   * Goes through the every placemark, style and properties object within a <Folder> tag
   *
   * @param kmlContainer Folder to obtain placemark and styles from
   */
  private void addContainerObjectToMap(KmlContainer kmlContainer, boolean isContainerVisible) {
    for (KmlPlacemark placemark : kmlContainer.getPlacemarks()) {
      boolean isPlacemarkVisible = getPlacemarkVisibility(placemark);
      boolean isObjectVisible = isContainerVisible && isPlacemarkVisible;
      Object mapObject = addPlacemarkToMap(placemark, isObjectVisible);
      kmlContainer.setPlacemark(placemark, mapObject);
    }
  }

  /**
   * Obtains the styleUrl from a placemark and finds the corresponding style in a list
   *
   * @param styleId StyleUrl from a placemark
   * @return Style which corresponds to an ID
   */
  private KmlStyle getPlacemarkStyle(String styleId) {
    KmlStyle style = mStylesRenderer.get(null);
    if (mStylesRenderer.get(styleId) != null) {
      style = mStylesRenderer.get(styleId);
    }
    return style;
  }

  /**
   * Sets the marker icon if there was a url that was found
   *
   * @param styleUrl      The style which we retrieve the icon url from
   * @param markerOptions The marker which is displaying the icon
   */
  private void addMarkerIcons(String styleUrl, MarkerOptions markerOptions) {
    if (getCachedBitmap(styleUrl) != null && !shouldForceDownload()) {
      // Bitmap stored in cache
      Bitmap bitmap = getCachedBitmap(styleUrl);
      markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
    } else if (!mMarkerIconUrls.contains(styleUrl)) {
      mMarkerIconUrls.add(styleUrl);
    }
  }

  /**
   * Determines if there are any icons to add to markers
   */
  private void downloadMarkerIcons() {
    mMarkerIconsDownloaded = true;
    for (Iterator<String> iterator = mMarkerIconUrls.iterator(); iterator.hasNext(); ) {
      String markerIconUrl = iterator.next();
      downloadMarkerIcon(markerIconUrl);
      iterator.remove();
    }
  }

  public abstract void downloadMarkerIcon(String markerIconUrl);

  public void onMarkerIconDownloaded(String markerIconUrl) {
    if (mLayerVisible) {
      addIconToMarkers(markerIconUrl, mPlacemarks);
      addContainerGroupIconsToMarkers(markerIconUrl, mContainers);
    }
  }

  /**
   * Adds the marker icon stored in mMarkerIconCache, to the {@link com.google.android.gms.maps.model.Marker}
   *
   * @param iconUrl icon url of icon to add to markers
   */
  void addIconToMarkers(String iconUrl, HashMap<KmlPlacemark, Object> placemarks) {
    for (KmlPlacemark placemark : placemarks.keySet()) {
      KmlStyle urlStyle = mStylesRenderer.get(placemark.getStyleId());
      KmlStyle inlineStyle = placemark.getInlineStyle();
      if ("Point".equals(placemark.getGeometry().getGeometryType())) {
        boolean isInlineStyleIcon = inlineStyle != null && iconUrl
            .equals(inlineStyle.getIconUrl());
        boolean isPlacemarkStyleIcon = urlStyle != null && iconUrl
            .equals(urlStyle.getIconUrl());
        if (isInlineStyleIcon) {
          scaleBitmap(inlineStyle, placemarks, placemark);
        } else if (isPlacemarkStyleIcon) {
          scaleBitmap(urlStyle, placemarks, placemark);
        }
      }
    }
  }

  /**
   * Enlarges or shrinks a bitmap image based on the scale provided
   * @param style     Style to retrieve iconUrl and scale from
   * @param placemark Placemark object to set the image to
   */
  private void scaleBitmap(KmlStyle style, HashMap<KmlPlacemark, Object> placemarks,
                           KmlPlacemark placemark) {
    final Bitmap bitmap = scaleBitmap(style);
    BitmapDescriptor scaledBitmap = BitmapDescriptorFactory.fromBitmap(bitmap);
    ((Marker) placemarks.get(placemark)).setIcon(scaledBitmap);
  }

  protected Bitmap scaleBitmap(KmlStyle style) {
    double bitmapScale = style.getIconScale();
    String bitmapUrl = style.getIconUrl();
    Bitmap bitmapImage = getCachedBitmap(bitmapUrl);
    return scaleIcon(mContext, bitmapImage, bitmapScale);
  }


  /**
   * Assigns icons to markers with a url if put in a placemark tag that is nested in a folder.
   *
   * @param iconUrl       url to obtain marker image
   * @param kmlContainers kml container which contains the marker
   */
  void addContainerGroupIconsToMarkers(String iconUrl,
                                               Iterable<KmlContainer> kmlContainers) {
    for (KmlContainer container : kmlContainers) {
      addIconToMarkers(iconUrl, container.getPlacemarksHashMap());
      if (container.hasContainers()) {
        addContainerGroupIconsToMarkers(iconUrl, container.getContainers());
      }
    }
  }

  /**
   * Adds a single geometry object to the map with its specified style
   *
   * @param geometry defines the type of object to add to the map
   * @param style    defines styling properties to add to the object when added to the map
   * @return the object that was added to the map, this is a Marker, Polyline, Polygon or an array
   * of either objects
   */
  private Object addToMap(KmlPlacemark placemark, KmlGeometry geometry, KmlStyle style,
                          KmlStyle inlineStyle, boolean isVisible) {

    String geometryType = geometry.getGeometryType();
    if (geometryType.equals("Point")) {
      Marker marker = addPointToMap(placemark, (KmlPoint) geometry, style, inlineStyle);
      marker.setVisible(isVisible);
      return marker;
    } else if (geometryType.equals("LineString")) {
      Polyline polyline = addLineStringToMap((KmlLineString) geometry, style, inlineStyle);
      polyline.setVisible(isVisible);
      return polyline;
    } else if (geometryType.equals("Polygon")) {
      Polygon polygon = addPolygonToMap((KmlPolygon) geometry, style, inlineStyle);
      polygon.setVisible(isVisible);
      return polygon;
    } else if (geometryType.equals("MultiGeometry")) {
      return addMultiGeometryToMap(placemark, (KmlMultiGeometry) geometry, style, inlineStyle,
          isVisible);
    }

    return null;
  }

  /**
   * Adds a KML Point to the map as a Marker by combining the styling and coordinates
   *
   * @param point contains coordinates for the Marker
   * @param style contains relevant styling properties for the Marker
   * @return Marker object
   */
  private Marker addPointToMap(KmlPlacemark placemark, KmlPoint point, KmlStyle style,
                               KmlStyle markerInlineStyle) {
    MarkerOptions markerUrlStyle = style.getMarkerOptions();
    markerUrlStyle.position(point.getGeometryObject());
    if (markerInlineStyle != null) {
      setInlinePointStyle(markerUrlStyle, markerInlineStyle, style.getIconUrl());
    } else if (style.getIconUrl() != null) {
      // Use shared style
      addMarkerIcons(style.getIconUrl(), markerUrlStyle);
    }
    Marker marker = mMap.addMarker(markerUrlStyle);
    setMarkerInfoWindow(style, marker, placemark);
    return marker;
  }

  /**
   * Sets a marker info window if no <text> tag was found in the KML document. This method sets
   * the marker title as the text found in the <name> start tag and the snippet as <description>
   *
   * @param style Style to apply
   */
  private void setMarkerInfoWindow(KmlStyle style, Marker marker,
                                   final KmlPlacemark placemark) {
    boolean hasName = placemark.hasProperty("name");
    boolean hasDescription = placemark.hasProperty("description");
    boolean hasBalloonOptions = style.hasBalloonStyle();
    boolean hasBalloonText = style.getBalloonOptions().containsKey("text");
    if (hasBalloonOptions && hasBalloonText) {
      marker.setTitle(parseTextVariables(style.getBalloonOptions().get("text"), placemark));
    } else if (hasBalloonOptions && hasName) {
      marker.setTitle(placemark.getProperty("name"));
    } else if (hasName && hasDescription) {
      marker.setTitle(placemark.getProperty("name"));
      marker.setSnippet(placemark.getProperty("description"));
    } else if (hasDescription) {
      marker.setTitle(placemark.getProperty("description"));
    }
  }


  private String parseTextVariables(final String text, final KmlPlacemark placemark) {
    final Matcher m = Pattern.compile("\\$\\[(\\w+)\\]").matcher(text);
    final StringBuffer buffer = new StringBuffer(text.length());
    while (m.find()) {
      String variable = m.group(1);
      String replacement = placemark.getProperty(variable);
      if (replacement == null) {
        replacement = "";
        Log.w("LOG_TAG", "Unknown template variable $[" + variable + "]");
      }
      m.appendReplacement(buffer, replacement);
    }
    m.appendTail(buffer);
    return buffer.toString();
  }

  /**
   * Sets the inline point style by copying over the styles that have been set
   *
   * @param markerOptions    marker options object to add inline styles to
   * @param inlineStyle      inline styles to apply
   * @param markerUrlIconUrl default marker icon URL from shared style
   */
  private void setInlinePointStyle(MarkerOptions markerOptions, KmlStyle inlineStyle,
                                   String markerUrlIconUrl) {
    MarkerOptions inlineMarkerOptions = inlineStyle.getMarkerOptions();
    if (inlineStyle.isStyleSet("heading")) {
      markerOptions.rotation(inlineMarkerOptions.getRotation());
    }
    if (inlineStyle.isStyleSet("hotSpot")) {
      markerOptions
          .anchor(inlineMarkerOptions.getAnchorU(), inlineMarkerOptions.getAnchorV());
    }
    if (inlineStyle.isStyleSet("markerColor")) {
      markerOptions.icon(inlineMarkerOptions.getIcon());
    }
    if (inlineStyle.isStyleSet("iconUrl")) {
      addMarkerIcons(inlineStyle.getIconUrl(), markerOptions);
    } else if (markerUrlIconUrl != null) {
      // Inline style with no icon defined
      addMarkerIcons(markerUrlIconUrl, markerOptions);
    }
  }

  /**
   * Adds a KML LineString to the map as a Polyline by combining the styling and coordinates
   *
   * @param lineString contains coordinates for the Polyline
   * @param style      contains relevant styling properties for the Polyline
   * @return Polyline object
   */
  private Polyline addLineStringToMap(KmlLineString lineString, KmlStyle style,
                                      KmlStyle inlineStyle) {
    PolylineOptions polylineOptions = style.getPolylineOptions();
    polylineOptions.addAll(lineString.getGeometryObject());
    if (inlineStyle != null) {
      setInlineLineStringStyle(polylineOptions, inlineStyle);
    } else if (style.isLineRandomColorMode()) {
      polylineOptions.color(KmlStyle.computeRandomColor(polylineOptions.getColor()));
    }
    return mMap.addPolyline(polylineOptions);
  }

  /**
   * Sets the inline linestring style by copying over the styles that have been set
   *
   * @param polylineOptions polygon options object to add inline styles to
   * @param inlineStyle     inline styles to apply
   */
  private void setInlineLineStringStyle(PolylineOptions polylineOptions, KmlStyle inlineStyle) {
    PolylineOptions inlinePolylineOptions = inlineStyle.getPolylineOptions();
    if (inlineStyle.isStyleSet("outlineColor")) {
      polylineOptions.color(inlinePolylineOptions.getColor());
    }
    if (inlineStyle.isStyleSet("width")) {
      polylineOptions.width(inlinePolylineOptions.getWidth());
    }
    if (inlineStyle.isLineRandomColorMode()) {
      polylineOptions.color(KmlStyle.computeRandomColor(inlinePolylineOptions.getColor()));
    }
  }

  /**
   * Adds a KML Polygon to the map as a Polygon by combining the styling and coordinates
   *
   * @param polygon contains coordinates for the Polygon
   * @param style   contains relevant styling properties for the Polygon
   * @return Polygon object
   */
  private Polygon addPolygonToMap(KmlPolygon polygon, KmlStyle style, KmlStyle inlineStyle) {
    PolygonOptions polygonOptions = style.getPolygonOptions();
    polygonOptions.addAll(polygon.getOuterBoundaryCoordinates());
    for (ArrayList<LatLng> innerBoundary : polygon.getInnerBoundaryCoordinates()) {
      polygonOptions.addHole(innerBoundary);
    }
    if (inlineStyle != null) {
      setInlinePolygonStyle(polygonOptions, inlineStyle);
    } else if (style.isPolyRandomColorMode()) {
      polygonOptions.fillColor(KmlStyle.computeRandomColor(polygonOptions.getFillColor()));
    }
    return mMap.addPolygon(polygonOptions);
  }

  /**
   * Sets the inline polygon style by copying over the styles that have been set
   *
   * @param polygonOptions polygon options object to add inline styles to
   * @param inlineStyle    inline styles to apply
   */
  private void setInlinePolygonStyle(PolygonOptions polygonOptions, KmlStyle inlineStyle) {
    PolygonOptions inlinePolygonOptions = inlineStyle.getPolygonOptions();
    if (inlineStyle.hasFill() && inlineStyle.isStyleSet("fillColor")) {
      polygonOptions.fillColor(inlinePolygonOptions.getFillColor());
    }
    if (inlineStyle.hasOutline()) {
      if (inlineStyle.isStyleSet("outlineColor")) {
        polygonOptions.strokeColor(inlinePolygonOptions.getStrokeColor());
      }
      if (inlineStyle.isStyleSet("width")) {
        polygonOptions.strokeWidth(inlinePolygonOptions.getStrokeWidth());
      }
    }
    if (inlineStyle.isPolyRandomColorMode()) {
      polygonOptions.fillColor(KmlStyle.computeRandomColor(inlinePolygonOptions.getFillColor()));
    }
  }

  /**
   * Adds all the geometries within a KML MultiGeometry to the map. Supports recursive
   * MultiGeometry. Combines styling of the placemark with the coordinates of each geometry.
   *
   * @param multiGeometry contains array of geometries for the MultiGeometry
   * @param urlStyle         contains relevant styling properties for the MultiGeometry
   * @return array of Marker, Polyline and Polygon objects
   */
  private ArrayList<Object> addMultiGeometryToMap(KmlPlacemark placemark,
                                                  KmlMultiGeometry multiGeometry, KmlStyle urlStyle, KmlStyle inlineStyle,
                                                  boolean isContainerVisible) {
    ArrayList<Object> mapObjects = new ArrayList<Object>();
    ArrayList<KmlGeometry> kmlObjects = multiGeometry.getGeometryObject();
    for (KmlGeometry kmlGeometry : kmlObjects) {
      mapObjects.add(addToMap(placemark, kmlGeometry, urlStyle, inlineStyle,
          isContainerVisible));
    }
    return mapObjects;
  }

  /**
   * Adds a ground overlay adds all the ground overlays onto the map and recursively adds all
   * ground overlays stored in the given containers
   *
   * @param groundOverlays ground overlays to add to the map
   * @param kmlContainers  containers to check for ground overlays
   */
  private void addGroundOverlays(HashMap<KmlGroundOverlay, GroundOverlay> groundOverlays,
                                 Iterable<KmlContainer> kmlContainers) {
    addGroundOverlays(groundOverlays);
    for (KmlContainer container : kmlContainers) {
      addGroundOverlays(container.getGroundOverlayHashMap(),
          container.getContainers());
    }
  }

  /**
   * Adds all given ground overlays to the map
   *
   * @param groundOverlays hashmap of ground overlays to add to the map
   */
  private void addGroundOverlays(HashMap<KmlGroundOverlay, GroundOverlay> groundOverlays) {
    for (KmlGroundOverlay groundOverlay : groundOverlays.keySet()) {
      String groundOverlayUrl = groundOverlay.getImageUrl();
      if (groundOverlayUrl != null && groundOverlay.getLatLngBox() != null) {
        // Can't draw overlay if url and coordinates are missing
        if (getCachedBitmap(groundOverlayUrl) != null) {
          addGroundOverlayToMap(groundOverlayUrl, mGroundOverlays, true);
        } else if (!mGroundOverlayUrls.contains(groundOverlayUrl)) {
          mGroundOverlayUrls.add(groundOverlayUrl);
        }
      }
    }
  }

  /**
   * Downloads images of all ground overlays
   */
  private void downloadGroundOverlays() {
    mGroundOverlayImagesDownloaded = true;
    for (Iterator<String> iterator = mGroundOverlayUrls.iterator(); iterator.hasNext(); ) {
      String groundOverlayUrl = iterator.next();
      downloadGroundOverlay(groundOverlayUrl);
      iterator.remove();
    }
  }

  public abstract void downloadGroundOverlay(String groundOverlayUrl);

  public void onGroundOverlayDownloaded(String url) {
    if (isLayerVisible()) {
      addGroundOverlayToMap(url, mGroundOverlays, true);
      addGroundOverlayInContainerGroups(url, mContainers, true);
    }
  }

  /**
   * Adds ground overlays from a given URL onto the map
   *
   * @param groundOverlayUrl url of ground overlay
   * @param groundOverlays   hashmap of ground overlays to add to the map
   */
  void addGroundOverlayToMap(String groundOverlayUrl,
                                     HashMap<KmlGroundOverlay, GroundOverlay> groundOverlays, boolean containerVisibility) {
    BitmapDescriptor groundOverlayBitmap = BitmapDescriptorFactory
        .fromBitmap(getCachedBitmap(groundOverlayUrl));
    for (KmlGroundOverlay kmlGroundOverlay : groundOverlays.keySet()) {
      if (kmlGroundOverlay.getImageUrl().equals(groundOverlayUrl)) {
        GroundOverlayOptions groundOverlayOptions = kmlGroundOverlay.getGroundOverlayOptions()
            .image(groundOverlayBitmap);
        GroundOverlay mapGroundOverlay = mMap.addGroundOverlay(groundOverlayOptions);
        if (!containerVisibility) {
          mapGroundOverlay.setVisible(false);
        }
        groundOverlays.put(kmlGroundOverlay, mapGroundOverlay);
      }
    }
  }

  /**
   * Adds ground overlays in containers from a given URL onto the map
   *
   * @param groundOverlayUrl url of ground overlay
   * @param kmlContainers    containers containing ground overlays to add to the map
   */
  void addGroundOverlayInContainerGroups(String groundOverlayUrl,
                                                 Iterable<KmlContainer> kmlContainers, boolean containerVisibility) {
    for (KmlContainer container : kmlContainers) {
      boolean isContainerVisible = getContainerVisibility(container, containerVisibility);
      addGroundOverlayToMap(groundOverlayUrl, container.getGroundOverlayHashMap(), isContainerVisible);
      if (container.hasContainers()) {
        addGroundOverlayInContainerGroups(groundOverlayUrl,
            container.getContainers(), isContainerVisible);
      }
    }
  }

  public abstract Bitmap getCachedBitmap(String url);
}
