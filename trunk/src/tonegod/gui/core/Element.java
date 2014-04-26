package tonegod.gui.core;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.LineWrapMode;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tonegod.gui.controls.extras.DragElement;
import tonegod.gui.controls.form.Form;
import tonegod.gui.controls.text.TextElement;
import tonegod.gui.core.utils.UIDUtil;
import tonegod.gui.effects.Effect;

/**
 *
 * @author t0neg0d
 */
public class Element extends Node {
	public static enum Borders {
		NW,
		N,
		NE,
		W,
		E,
		SW,
		S,
		SE;
	};
	/**
	 * Some controls provide different layout's based on the orientation of the control
	 */
	public static enum Orientation {
		/**
		 * Vertical layout
		 */
		VERTICAL,
		/**
		 * Horizontal layout
		 */
		HORIZONTAL
	}
	/**
	 * Defines how the element will dock to it's parent element during resize events
	 */
	public static enum Docking {
		/**
		 * Docks to the top left of parent
		 */
		NW,
		/**
		 * Docks to the top right of parent
		 */
		NE,
		/**
		 * Docks to the bottom left of parent
		 */
		SW,
		/**
		 * Docks to the bottom right of parent
		 */
		SE
	}
	
	protected Application app;
	protected ElementManager screen;
	private String UID;
	private Vector2f position = new Vector2f();
	public Vector2f orgPosition;
	private Vector2f dimensions = new Vector2f();
	public Vector2f orgDimensions, orgRelDimensions;
	public Vector4f borders = new Vector4f(1,1,1,1);
	public Vector4f borderHandles = new Vector4f(12,12,12,12);
	private Vector2f minDimensions = new Vector2f(10, 10);
	
	private boolean ignoreMouse = false;
	protected boolean isMovable = false;
	private boolean lockToParentBounds = false;
	private boolean isResizable = false;
	private boolean resizeN = true;
	private boolean resizeS = true;
	private boolean resizeW = true;
	private boolean resizeE = true;
	private boolean dockN = false;
	private boolean dockW = true;
	private boolean dockE = false;
	private boolean dockS = true;
	private boolean scaleNS = true;
	private boolean scaleEW = true;
	
	private boolean effectParent = false;
	private boolean effectAbsoluteParent = false;
	
	private Geometry geom;
	private ElementQuadGrid model;
	private boolean tileImage = false;
	private Material mat;
	private Texture defaultTex;
	private boolean useLocalAtlas = false;
	private String atlasCoords = "";
	private Texture alphaMap = null;
	
	protected BitmapText textElement;
//	protected TextElement textElement;
	protected Vector2f textPosition = new Vector2f(0,0);
	protected LineWrapMode textWrap = LineWrapMode.Word;
	protected BitmapFont.Align textAlign = BitmapFont.Align.Left;
	protected BitmapFont.VAlign textVAlign = BitmapFont.VAlign.Top;
	protected String text = "";
	private String toolTipText = null;
	
	protected BitmapFont font;
	protected float fontSize = 20;
	protected float textPadding = 0;
	protected ColorRGBA fontColor = ColorRGBA.White;
	private ColorRGBA defaultColor = new ColorRGBA(1,1,1,0);
	
	private Element elementParent = null;
	protected Map<String, Element> elementChildren = new LinkedHashMap();
	
	protected boolean isClipped = false;
	protected boolean wasClipped = false;
	private Element clippingLayer;
	private Vector4f clippingBounds = new Vector4f();
	private float clipPadding = 0;
	private float textClipPadding = 0;
	protected boolean isVisible = true;
	protected boolean wasVisible = true;
	protected boolean isVisibleAsModal = false;
	private boolean hasFocus = false;
	private boolean resetKeyboardFocus = true;
	
	private Form form;
	private int tabIndex = 0;
	
	private float zOrder;
	private boolean effectZOrder = true;
	private Map<Effect.EffectEvent, Effect> effects = new HashMap();
	
	private OSRBridge bridge;
	
	private boolean ignoreGlobalAlpha = false;
	private boolean isModal = false;
	private boolean isGlobalModal = false;
	
	private Object elementUserData;
	
	private boolean initialized = false;
	
	private boolean isDragElement = false, isDropElement = false;
	
	private Docking docking = Docking.NW;
	private Orientation orientation = Orientation.HORIZONTAL;
	
	/**
	 * The Element class is the single primitive for all controls in the gui library.
	 * Each element consists of an ElementQuadMesh for rendering resizable textures,
	 * as well as a BitmapText element if setText(String text) is called.
	 * 
	 * Behaviors, such as movement and resizing, are common to all elements and can
	 * be enabled/disabled to ensure the element reacts to user input as needed.
	 * 
	 * @param screen The Screen control the element or it's absolute parent element is being added to
	 * @param UID A unique String identifier used when looking up elements by screen.getElementByID()
	 * @param position A Vector2f containing the x/y coordinates (relative to it's parent elements x/y) for positioning
	 * @param dimensions A Vector2f containing the dimensions of the element, x being width, y being height
	 * @param resizeBorders A Vector4f containing the size of each border used for scaling images without distorting them (x = N, y = W, x = E, w = S)
	 * @param texturePath A String path to the default image to be rendered on the element's mesh
	 */
	public Element(ElementManager screen, String UID, Vector2f position, Vector2f dimensions, Vector4f resizeBorders, String texturePath) {
		this.app = screen.getApplication();
		this.screen = screen;
		if (UID == null) {
			this.UID = UIDUtil.getUID();
		} else {
			this.UID = UID;
		}
		this.position.set(position);
		this.dimensions.set(dimensions);
		this.orgDimensions = dimensions.clone();
		this.orgRelDimensions = new Vector2f(1,1);
		this.borders.set(resizeBorders);
		
		BitmapFont tempFont = app.getAssetManager().loadFont(screen.getStyle("Font").getString("defaultFont"));
		
		font = new BitmapFont();
		font.setCharSet(app.getAssetManager().loadFont(screen.getStyle("Font").getString("defaultFont")).getCharSet());
		Material[] pages = new Material[tempFont.getPageSize()];
		for (int i = 0; i < pages.length; i++) {
			pages[i] = tempFont.getPage(i).clone();
		}
		font.setPages(pages);
		
		float imgWidth = 100;
		float imgHeight = 100;
		float pixelWidth = 1f/imgWidth;
		float pixelHeight = 1f/imgHeight;
		float textureAtlasX = 0, textureAtlasY = 0, textureAtlasW = imgWidth, textureAtlasH = imgHeight;
		
		boolean useAtlas = screen.getUseTextureAtlas();
		
		if (texturePath != null) {
			if (useAtlas) {
				float[] coords = screen.parseAtlasCoords(texturePath);
				textureAtlasX = coords[0];
				textureAtlasY = coords[1];
				textureAtlasW = coords[2];
				textureAtlasH = coords[3];
				
				this.atlasCoords = "x=" + coords[0] + "|y=" + coords[1] + "|w=" + coords[2] + "|h=" + coords[3];
				
				defaultTex = screen.getAtlasTexture();

				imgWidth = defaultTex.getImage().getWidth();
				imgHeight = defaultTex.getImage().getHeight();
				pixelWidth = 1f/imgWidth;
				pixelHeight = 1f/imgHeight;

				textureAtlasY = imgHeight-textureAtlasY-textureAtlasH;
			} else {
				defaultTex = app.getAssetManager().loadTexture(texturePath);
				defaultTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
				defaultTex.setMagFilter(Texture.MagFilter.Nearest);
				defaultTex.setWrap(Texture.WrapMode.Clamp);

				imgWidth = defaultTex.getImage().getWidth();
				imgHeight = defaultTex.getImage().getHeight();
				pixelWidth = 1f/imgWidth;
				pixelHeight = 1f/imgHeight;

				textureAtlasW = imgWidth;
				textureAtlasH = imgHeight;
			}
		}
		mat = new Material(app.getAssetManager(), "tonegod/gui/shaders/Unshaded.j3md");
		if (texturePath != null) {
			mat.setTexture("ColorMap", defaultTex);
			mat.setColor("Color", new ColorRGBA(1,1,1,1));
		} else {
			mat.setColor("Color", defaultColor);
		}
		if (useAtlas) mat.setBoolean("UseEffectTexCoords", true);
		mat.setVector2("OffsetAlphaTexCoord", new Vector2f(0,0));
		mat.setFloat("GlobalAlpha", screen.getGlobalAlpha());
		
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Back);
		
		this.model = new ElementQuadGrid(this.dimensions, borders, imgWidth, imgHeight, pixelWidth, pixelHeight, textureAtlasX, textureAtlasY, textureAtlasW, textureAtlasH);
		
        this.setName(UID + ":Node");
                
		geom = new Geometry(UID + ":Geometry");
		geom.setMesh(model);
		geom.setCullHint(CullHint.Never);
        geom.setQueueBucket(Bucket.Gui);
		geom.setMaterial(mat);
                
		this.attachChild(geom);
		
		this.setQueueBucket(Bucket.Gui);
		
		this.setLocalTranslation(position.x, position.y, 0);
	}
	
	public void setAsContainerOnly() {
		detachChildAt(0);
	}
	
	private void throwParserException() {
		try {
			throw new java.text.ParseException("The provided texture information does not conform to the expected standard of ?x=(int)&y=(int)&w=(int)&h=(int)", 0);
		} catch (ParseException ex) {
			Logger.getLogger(Element.class.getName()).log(Level.SEVERE, "The provided texture information does not conform to the expected standard of ?x=(int)&y=(int)&w=(int)&h=(int)", ex);
		}
	}
	
	/**
	 * Sets the texture to use as an atlas image as well as the atlas image coords.
	 * @param tex The texture to use as a local atlas image
	 * @param queryString The position of the desire atlas image (e.g. "x=0|y=0|w=50|h=50")
	 */
	public void setTextureAtlasImage(Texture tex, String queryString) {
		this.defaultTex = tex;
		mat.setTexture("ColorMap", tex);
		mat.setColor("Color", new ColorRGBA(1,1,1,1));
		mat.setBoolean("UseEffectTexCoords", true);
		
		this.useLocalAtlas = true;
		this.atlasCoords = queryString;
		
		float[] coords = screen.parseAtlasCoords(queryString);
		float textureAtlasX = coords[0];
		float textureAtlasY = coords[1];
		float textureAtlasW = coords[2];
		float textureAtlasH = coords[3];

		float imgWidth = defaultTex.getImage().getWidth();
		float imgHeight = defaultTex.getImage().getHeight();
		float pixelWidth = 1f/imgWidth;
		float pixelHeight = 1f/imgHeight;

		textureAtlasY = imgHeight-textureAtlasY-textureAtlasH;
		
		this.model = new ElementQuadGrid(this.dimensions, borders, imgWidth, imgHeight, pixelWidth, pixelHeight, textureAtlasX, textureAtlasY, textureAtlasW, textureAtlasH);
		geom.setMesh(model);
	}
	
	/**
	 * Returns the current unparsed string representing the Element's atlas image
	 * @return 
	 */
	public String getAtlasCoords() { return this.atlasCoords; }
	
	/**
	 * Sets the element image to the specified x/y/width/height
	 * @param queryString (e.g. "x=0|y=0|w=50|h=50")
	 */
	public void updateTextureAtlasImage(String queryString) {
		float[] coords = screen.parseAtlasCoords(queryString);
		float textureAtlasX = coords[0];
		float textureAtlasY = coords[1];
		float textureAtlasW = coords[2];
		float textureAtlasH = coords[3];

		float imgWidth = defaultTex.getImage().getWidth();
		float imgHeight = defaultTex.getImage().getHeight();
		float pixelWidth = 1f/imgWidth;
		float pixelHeight = 1f/imgHeight;

		textureAtlasY = imgHeight-textureAtlasY-textureAtlasH;
		
		getModel().updateTexCoords(textureAtlasX, textureAtlasY, textureAtlasW, textureAtlasH);
	}
	
	/**
	 * Returns if the element is using a local texture atlas of the screen defined texture atlas
	 * @return 
	 */
	public boolean getUseLocalAtlas() { return this.useLocalAtlas; }
	
	/**
	 * Returns the difference between the placement of the elements current image and the given texture coords.
	 * @param coords The x/y coords of the new image
	 * @return Vector2f containing The difference between the given coords and the original image
	 */
	public Vector2f getAtlasTextureOffset(float[] coords) {
		Texture tex;
		if (defaultTex != null) tex = defaultTex;
		else					tex = screen.getAtlasTexture();
		float imgWidth = tex.getImage().getWidth();
		float imgHeight = tex.getImage().getHeight();
		float pixelWidth = 1f/imgWidth;
		float pixelHeight = 1f/imgHeight;

		return new Vector2f( getModel().getEffectOffset( pixelWidth*coords[0], pixelHeight*(imgHeight-coords[1]-coords[3]) ));
	}
	
	public void setTileImage(boolean tileImage) {
		this.tileImage = tileImage;
		if (tileImage)
			((Texture)mat.getParam("ColorMap").getValue()).setWrap(Texture.WrapMode.Repeat);
		else
			((Texture)mat.getParam("ColorMap").getValue()).setWrap(Texture.WrapMode.Clamp);
		setDimensions(dimensions);
	}
	
	public boolean getTileImage() {
		return this.tileImage;
	}
	
	/**
	 * Converts the the inputed percentage (0.0f-1.0f) into pixels of the elements image
	 * @param in Vector2f containing the x and y percentage
	 * @return Vector2f containing the actual width/height in pixels
	 */
	public final Vector2f getV2fPercentToPixels(Vector2f in) {
		if (getElementParent() == null) {
			if (in.x < 1) in.setX(screen.getWidth()*in.x);
			if (in.y < 1) in.setY(screen.getHeight()*in.y);
		} else {
			if (in.x < 1) in.setX(getElementParent().getWidth()*in.x);
			if (in.y < 1) in.setY(getElementParent().getHeight()*in.y);
		}
		return in;
	}
	
	/**
	 * Adds the specified Element as a child to this Element.
	 * @param child The Element to add as a child
	 */
	public void addChild(Element child) {
		child.elementParent = this;
		
		if (!child.getInitialized()) {
			child.setY(this.getHeight()-child.getHeight()-child.getY());
			child.orgPosition = position.clone();
			child.orgPosition.setY(child.getY());
			child.setInitialized();
		}
		child.orgRelDimensions.set(child.getWidth()/getWidth(),child.getHeight()/getHeight());
		child.setQueueBucket(RenderQueue.Bucket.Gui);
		
		if (screen.getElementById(child.getUID()) != null) {
			try {
				throw new ConflictingIDException();
			} catch (ConflictingIDException ex) {
				Logger.getLogger(Element.class.getName()).log(Level.SEVERE, "The child element '" + child.getUID() + "' (" + child.getClass() + ") conflicts with a previously added child element in parent element '" + getUID() + "'.", ex);
				System.exit(0);
			}
		} else {
			elementChildren.put(child.getUID(), child);
			this.attachChild(child);
		}
	}
	
	/**
	 * Adds the specified Element as a child to this Element.
	 * @param child The Element to add as a child
	 */
	public void addChild(Element child, boolean hide) {
		child.elementParent = this;
		
		if (!child.getInitialized()) {
			child.setY(this.getHeight()-child.getHeight()-child.getY());
			child.orgPosition = position.clone();
			child.orgPosition.setY(child.getY());
			child.setInitialized();
		}
		child.orgRelDimensions.set(child.getWidth()/getWidth(),child.getHeight()/getHeight());
		child.setQueueBucket(RenderQueue.Bucket.Gui);
		
		if (screen.getElementById(child.getUID()) != null) {
			try {
				throw new ConflictingIDException();
			} catch (ConflictingIDException ex) {
				Logger.getLogger(Element.class.getName()).log(Level.SEVERE, "The child element '" + child.getUID() + "' (" + child.getClass() + ") conflicts with a previously added child element in parent element '" + getUID() + "'.", ex);
				System.exit(0);
			}
		} else {
			elementChildren.put(child.getUID(), child);
			this.attachChild(child);
			
			if (hide)
				child.hide();
		}
	}
	
	/**
	 * Removes the specified Element
	 * @param child Element to remove
	 */
	public void removeChild(Element child) {
		Element e = elementChildren.remove(child.getUID());
		if (e != null) {
			e.elementParent = null;
			e.removeFromParent();
			e.cleanup();
		}
	}
	
	/**
	 * Remove all child Elements from this Element
	 */
	public void removeAllChildren() {
		for (Element e : elementChildren.values()) {
			e.removeFromParent();
		}
		elementChildren.clear();
	}
	
	/**
	 * Returns the child elements as a Map
	 * @return 
	 */
	public Map<String, Element> getElementsAsMap() {
		return this.elementChildren;
	}
	
	/**
	 * Returns the child elements as a Collection
	 * @return 
	 */
	public Collection<Element> getElements() {
		return this.elementChildren.values();
	}
	
	/**
	 * Returns the one and only Element's screen
	 * @return 
	 */
	public ElementManager getScreen() {
		return this.screen;
	}
	
	// Z-ORDER 
	/**
	 * Recursive call made by the screen control to properly initialize z-order (depth) placement
	 * @param zOrder The depth to place the Element at. (Relative to the parent's z-order)
	 */
	protected void initZOrder(float zOrder) {
		setLocalTranslation(getLocalTranslation().setZ(
			zOrder
		));
		if (getTextElement() != null)
			getTextElement().setLocalTranslation(getTextElement().getLocalTranslation().setZ(
				screen.getZOrderStepMinor()
			));
		
		for (Element el : elementChildren.values()) {
			el.initZOrder(screen.getZOrderStepMinor());
		}
	}
	
	/**
	 * Returns the Element's zOrder
	 * @return float zOrder
	 */
	public float getZOrder() {
		return this.zOrder;
	}
	
	/**
	 * Sets the Elements zOrder (I would suggest NOT using this method)
	 * @param zOrder 
	 */
	public void setZOrder(float zOrder) {
		this.zOrder = zOrder;
		initZOrder(zOrder);
	}
	
	public void setEffectZOrder(boolean effectZOrder) {
		this.effectZOrder = effectZOrder;
	}
	
	public boolean getEffectZOrder() { return this.effectZOrder; }
	
	public void setGlobalUIScale(float widthPercent, float heightPercent) {
		for (Element el : elementChildren.values()) {
			el.setPosition(el.getPosition().x*widthPercent, el.getPosition().y*heightPercent);
			el.setDimensions(el.getDimensions().x*widthPercent, el.getDimensions().y*heightPercent);
			el.setFontSize(el.getFontSize()*heightPercent);
			el.setGlobalUIScale(widthPercent, heightPercent);
		}
	}
	
	/**
	 * Returns a list of all children that are an instance of DragElement
	 * @return List<Element>
	 */
	public List<Element> getDraggableChildren() {
		List<Element> ret = new ArrayList();
		for (Element el : elementChildren.values()) {
			if (el instanceof DragElement) {
				ret.add(el);
			}
		}
		return ret;
	}
	
	// Recursive & non-recursive parent/child element searches
	/**
	 * Recursively searches children elements for specified element containing the specified UID
	 * @param UID - Unique Indentifier of element to search for
	 * @return Element containing UID or null if not found
	 */
	public Element getChildElementById(String UID) {
		Element ret = null;
		if (this.UID.equals(UID)) {
			ret = this;
		} else {
			if (elementChildren.containsKey(UID)) {
				ret = elementChildren.get(UID);
			} else {
				for (Element el : elementChildren.values()) {
					ret = el.getChildElementById(UID);
					if (ret != null) {
						break;
					}
				}
			}
		}
		return ret;
	}
	
	/**
	 * Returns the top-most parent in the tree of Elements.  The topmost element will always have a parent of null
	 * @return Element elementParent
	 */
	public Element getAbsoluteParent() {
		if (elementParent == null) {
			return this;
		} else {
			return elementParent.getAbsoluteParent();
		}
	}
	
	/**
	 * Returns the parent element of this node
	 * @return Element elementParent
	 */
	public Element getElementParent() {
		return elementParent;
	}
	
	/**
	 * Sets the element's parent element
	 * @param elementParent Element
	 */
	public void setElementParent(Element elementParent) {
		this.elementParent = elementParent;
	}
	
	// Getters & Setters
	/**
	 * Allows for setting the Element UID if (and ONLY if) the Element Parent is null
	 * @param UID The new UID
	 * @return boolean If setting the UID was successful
	 */
	public boolean setUID(String UID) {
		if (this.elementParent == null) {
			this.UID = UID;
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Returns the element's unique string identifier
	 * @return String UID
	 */
	public String getUID() {
		return UID;
	}
	
	/**
	 * Returns the default material for the element
	 * @return Material mat
	 */
	public Material getMaterial() {
		return this.mat;
	}
	
	/**
	 * A way to override the default material of the element.
	 * 
	 * NOTE: It is important that the shader used with the new material is either:
	 * A: The provided Unshaded material contained with this library, or
	 * B: The custom shader contains the caret, text range, clipping and effect 
	 *    handling provided in the default shader.
	 * 
	 * @param mat The Material to use for rendering this Element.
	 */
	public void setLocalMaterial(Material mat) {
		this.mat = mat;
		this.setMaterial(mat);
	}
	
	/**
	 * Informs the screen control that this Element should be ignored by mouse events.
	 * 
	 * @param ignoreMouse boolean
	 */
	public void setIgnoreMouse(boolean ignoreMouse) {
		this.ignoreMouse = ignoreMouse;
	}
	
	/**
	 * Returns if the element is set to ingnore mouse events
	 * 
	 * @return boolean ignoreMouse
	 */
	public boolean getIgnoreMouse() {
		return this.ignoreMouse;
	}
	
	/**
	 * Enables draggable behavior for this element
	 * @param isMovable boolean
	 */
	public void setIsMovable(boolean isMovable) {
		this.isMovable = isMovable;
	}
	
	/**
	 * Returns if the element has draggable behavior set
	 * @return boolean isMovable
	 */
	public boolean getIsMovable() {
		return this.isMovable;
	}
	
	/**
	 * Enables resize behavior for this element
	 * @param isResizable boolean
	 */
	public void setIsResizable(boolean isResizable) {
		this.isResizable = isResizable;
	}
	
	/**
	 * Returns if the element has resize behavior set
	 * @return boolean isResizable
	 */
	public boolean getIsResizable() {
		return this.isResizable;
	}
	
	/**
	 * Enables/disables north border for resizing
	 * @param resizeN boolean
	 */
	public void setResizeN(boolean resizeN) {
		this.resizeS = resizeN;
	}
	
	/**
	 * Returns whether the elements north border has enabled/disabled resizing
	 * @return boolean resizeN
	 */
	public boolean getResizeN() {
		return this.resizeS;
	}
	
	/**
	 * Enables/disables south border for resizing
	 * @param resizeS boolean
	 */
	public void setResizeS(boolean resizeS) {
		this.resizeN = resizeS;
	}
	
	/**
	 * Returns whether the elements south border has enabled/disabled resizing
	 * @return boolean resizeS
	 */
	public boolean getResizeS() {
		return this.resizeN;
	}
	
	/**
	 * Enables/disables west border for resizing
	 * @param resizeW boolean
	 */
	public void setResizeW(boolean resizeW) {
		this.resizeW = resizeW;
	}
	
	/**
	 * Returns whether the elements west border has enabled/disabled resizing
	 * @return boolean resizeW
	 */
	public boolean getResizeW() {
		return this.resizeW;
	}
	
	/**
	 * Enables/disables east border for resizing
	 * @param resizeE boolean
	 */
	public void setResizeE(boolean resizeE) {
		this.resizeE = resizeE;
	}
	
	/**
	 * Returns whether the elements east border has enabled/disabled resizing
	 * @return boolean resizeE
	 */
	public boolean getResizeE() {
		return this.resizeE;
	}
	
	/**
	 * Sets how the element will docking to it's parent element during resize events.
	 * NW = Top Left of parent element
	 * NE = Top Right of parent element
	 * SW = Bottom Left of parent element
	 * SE = Bottom Right of parent element
	 * @param docking 
	 */
	public void setDocking(Docking docking) {
		this.docking = docking;
	}
	
	public Docking getDocking() {
		return this.docking;
	}
	/**
	 * Enables north docking of element (disables south docking of Element).  This
	 * determines how the Element should retain positioning on parent resize events.
	 * 
	 * @param dockN boolean
	 */
	@Deprecated
	public void setDockN(boolean dockN) {
		this.dockS = dockN;
		this.dockN = !dockN;
		Docking d = null;
		if (dockS) {
			if (dockE)	d = Docking.NE;
			else		d = Docking.NW;
		} else {
			if (dockE)	d = Docking.SE;
			else		d = Docking.SW;
		}
		setDocking(d);
	}
	
	/**
	 * Returns if the Element is docked to the north quadrant of it's parent element.
	 * @return boolean dockN
	 */
	@Deprecated
	public boolean getDockN() {
		return this.dockS;
	}
	
	/**
	 * Enables west docking of Element (disables east docking of Element).  This
	 * determines how the Element should retain positioning on parent resize events.
	 * 
	 * @param dockW boolean
	 */
	@Deprecated
	public void setDockW(boolean dockW) {
		this.dockW = dockW;
		this.dockE = !dockW;
		Docking d = null;
		if (dockE) {
			if (dockS)	d = Docking.NE;
			else		d = Docking.SE;
		} else {
			if (dockS)	d = Docking.NW;
			else		d = Docking.SW;
		}
		setDocking(d);
	}
	
	/**
	 * Returns if the Element is docked to the west quadrant of it's parent element.
	 * @return boolean dockW
	 */
	@Deprecated
	public boolean getDockW() {
		return this.dockW;
	}
	
	/**
	 * Enables east docking of Element (disables west docking of Element).  This
	 * determines how the Element should retain positioning on parent resize events.
	 * 
	 * @param dockE boolean
	 */
	@Deprecated
	public void setDockE(boolean dockE) {
		this.dockE = dockE;
		this.dockW = !dockE;
		Docking d = null;
		if (dockE) {
			if (dockS)	d = Docking.NE;
			else		d = Docking.SE;
		} else {
			if (dockS)	d = Docking.NW;
			else		d = Docking.SW;
		}
		setDocking(d);
	}
	
	/**
	 * Returns if the Element is docked to the east quadrant of it's parent element.
	 * @return boolean dockE
	 */
	@Deprecated
	public boolean getDockE() {
		return this.dockE;
	}
	
	/**
	 * Enables south docking of Element (disables north docking of Element).  This
	 * determines how the Element should retain positioning on parent resize events.
	 * 
	 * @param dockS boolean
	 */
	@Deprecated
	public void setDockS(boolean dockS) {
		this.dockN = dockS;
		this.dockS = !dockS;
		Docking d = null;
		if (dockS) {
			if (dockE)	d = Docking.NE;
			else		d = Docking.NW;
		} else {
			if (dockE)	d = Docking.SE;
			else		d = Docking.SW;
		}
		setDocking(d);
	}
	
	/**
	 * Returns if the Element is docked to the south quadrant of it's parent element.
	 * @return boolean dockS
	 */
	@Deprecated
	public boolean getDockS() {
		return this.dockN;
	}
	
	/**
	 * Determines if the element should scale with parent when resized vertically.
	 * @param scaleNS boolean
	 */
	public void setScaleNS(boolean scaleNS) {
		this.scaleNS = scaleNS;
	}
	
	/**
	 * Returns if the Element is set to scale vertically when it's parent Element is
	 * resized.
	 * 
	 * @return boolean scaleNS
	 */
	public boolean getScaleNS() {
		return this.scaleNS;
	}
	
	/**
	 * Determines if the element should scale with parent when resized horizontally.
	 * @param scaleEW boolean
	 */
	public void setScaleEW(boolean scaleEW) {
		this.scaleEW = scaleEW;
	}
	
	/**
	 * Returns if the Element is set to scale horizontally when it's parent Element is
	 * resized.
	 * 
	 * @return boolean scaleEW
	 */
	public boolean getScaleEW() {
		return this.scaleEW;
	}
	
	/**
	 * Sets the element to pass certain events (movement, resizing) to it direct parent instead
	 * of effecting itself.
	 * 
	 * @param effectParent boolean
	 */
	public void setEffectParent(boolean effectParent) {
		this.effectParent = effectParent;
	}
	
	/**
	 * Returns if the Element is set to pass events to it's direct parent
	 * @return boolean effectParent
	 */
	public boolean getEffectParent() {
		return this.effectParent;
	}
	
	/**
	 * Sets the element to pass certain events (movement, resizing) to it absolute
	 * parent instead of effecting itself.
	 * 
	 * The Elements absolute parent is the element farthest up in it's nesting order, 
	 * or simply put, was added to the screen.
	 * 
	 * @param effectAbsoluteParent boolean
	 */
	public void setEffectAbsoluteParent(boolean effectAbsoluteParent) {
		this.effectAbsoluteParent = effectAbsoluteParent;
	}
	
	/**
	 * Returns if the Element is set to pass events to it's absolute parent
	 * @return boolean effectParent
	 */
	public boolean getEffectAbsoluteParent() {
		return this.effectAbsoluteParent;
	}
	
	/**
	 * Forces the object to stay within the constraints of it's parent Elements
	 * dimensions.
	 * NOTE: use setLockToParentBounds instead.
	 * 
	 * @param lockToParentBounds boolean
	 */
	@Deprecated
	public void setlockToParentBounds(boolean lockToParentBounds) {
		this.lockToParentBounds = lockToParentBounds;
	}/**
	 * Forces the object to stay within the constraints of it's parent Elements
	 * dimensions.
	 * 
	 * @param lockToParentBounds boolean
	 */
	public void setLockToParentBounds(boolean lockToParentBounds) {
		this.lockToParentBounds = lockToParentBounds;
	}
	
	/**
	 * Returns if the Element has been constrained to it's parent Element's dimensions.
	 * 
	 * @return boolean lockToParentBounds
	 */
	public boolean getLockToParentBounds() {
		return this.lockToParentBounds;
	}
	
	/**
	 * Set the x,y coordinates of the Element.  X and y are relative to the parent
	 * Element.
	 * 
	 * @param position Vector2f screen poisition of Element
	 */
	public void setPosition(Vector2f position) {
		this.position.set(position);
		updateNodeLocation();
	}
	
	/**
	 * Set the x,y coordinates of the Element.  X and y are relative to the parent
	 * Element.
	 * 
	 * @param x The x coordinate screen poisition of Element
	 * @param y The y coordinate screen poisition of Element
	 */
	public void setPosition(float x, float y) {
		this.position.setX(x);
		this.position.setY(y);
		updateNodeLocation();
	}
	
	/**
	 * Set the x coordinates of the Element.  X is relative to the parent Element.
	 * 
	 * @param x The x coordinate screen poisition of Element
	 */
	public void setX(float x) {
		this.position.setX(x);
		updateNodeLocation();
	}
	
	/**
	 * Set the y coordinates of the Element.  Y is relative to the parent Element.
	 * 
	 * @param y The y coordinate screen poisition of Element
	 */
	public void setY(float y) {
		this.position.setY(y);
		updateNodeLocation();
	}
	
	private void updateNodeLocation() {
		this.setLocalTranslation(position.x, position.y, this.getLocalTranslation().getZ());
		updateClipping();
	}
	
	/**
	 * Flags Element as Drag Element for Drag & Drop interaction
	 * @param isDragElement boolean
	 */
	public void setIsDragDropDragElement(boolean isDragElement) {
		this.isDragElement = isDragElement;
		if (isDragElement)
			this.isDropElement = false;
	}
	
	/**
	 * Returns if the Element is currently flagged as a Drag Element for Drag & Drop interaction
	 * @return boolean
	 */
	public boolean getIsDragDropDragElement() {
		return this.isDragElement;
	}
	
	/**
	 * Flags Element as Drop Element for Drag & Drop interaction
	 * @param isDropElement boolean
	 */
	public void setIsDragDropDropElement(boolean isDropElement) {
		this.isDropElement = isDropElement;
		if (isDropElement)
			this.isDragElement = false;
	}
	
	/**
	 * Returns if the Element is currently flagged as a Drop Element for Drag & Drop interaction
	 * @return boolean
	 */
	public boolean getIsDragDropDropElement() {
		return this.isDropElement;
	}
	
	/**
	 * Returns the current screen location of the Element
	 * @return Vector2f position
	 */
	public Vector2f getPosition() {
		return position;
	}
	
	/**
	 * Gets the relative x coordinate of the Element from it's parent Element's x
	 * @return  float
	 */
	public float getX() {
		return position.x;
	}
	
	/**
	 * Gets the relative y coordinate of the Element from it's parent Element's y
	 * @return  float
	 */
	public float getY() {
		return position.y;
	}
	
	/**
	 * Returns the x coord of an element from screen x 0, ignoring the nesting order.
	 * @return  float x
	 */
	public float getAbsoluteX() {
		float x = getX();
		Element el = this;
		while (el.getElementParent() != null) {
			el = el.getElementParent();
			x += el.getX();
		}
		return x;
	}
	
	/**
	 * Returns the y coord of an element from screen y 0, ignoring the nesting order.
	 * @return float
	 */
	public float getAbsoluteY() {
		float y = getY();
		Element el = this;
		while (el.getElementParent() != null) {
			el = el.getElementParent();
			y += el.getY();
		}
		return y;
	}
	
	/**
	 * Sets the width and height of the element
	 * @param w float
	 * @param h float
	 */
	public void setDimensions(float w, float h) {
		this.dimensions.setX(w);
		this.dimensions.setY(h);
		getModel().updateDimensions(dimensions.x, dimensions.y);
		if (tileImage) {
			float tcW = dimensions.x/getModel().getImageWidth();
			float tcH = dimensions.y/getModel().getImageHeight();
			getModel().updateTiledTexCoords(0, -tcH, tcW, 0);
		}
		geom.updateModelBound();
		if (textElement != null) {
			updateTextElement();
		}
		updateClipping();
	}
	
	/**
	 * Sets the width and height of the element
	 * @param dimensions Vector2f
	 */
	public void setDimensions(Vector2f dimensions) {
		this.dimensions.set(dimensions);
		getModel().updateDimensions(dimensions.x, dimensions.y);
		if (tileImage) {
			float tcW = dimensions.x/getModel().getImageWidth();
			float tcH = dimensions.y/getModel().getImageHeight();
			getModel().updateTiledTexCoords(0, -tcH, tcW, 0);
		}
		geom.updateModelBound();
		if (textElement != null) {
			updateTextElement();
		}
		updateClipping();
	}
	
	/**
	 * Stubbed for future use.  This should limit resizing to the minimum dimensions defined
	 * @param minDimensions The absolute minimum dimensions for this Element.
	 */
	public void setMinDimensions(Vector2f minDimensions) {
		if (this.minDimensions == null) this.minDimensions = new Vector2f();
		this.minDimensions.set(minDimensions);
	}
	
	/**
	 * Sets the width of the element
	 * @param width float
	 */
	public void setWidth(float width) {
		this.dimensions.setX(width);
		getModel().updateWidth(dimensions.x);
		if (tileImage) {
			float tcW = dimensions.x/getModel().getImageWidth();
			float tcH = dimensions.y/getModel().getImageHeight();
			getModel().updateTiledTexCoords(0, -tcH, tcW, 0);
		}
		geom.updateModelBound();
		if (textElement != null) {
			updateTextElement();
		}
		updateClipping();
	}
	
	/**
	 * Sets the height of the element
	 * @param height float
	 */
	public void setHeight(float height) {
		this.dimensions.setY(height);
		getModel().updateHeight(dimensions.y);
		if (tileImage) {
			float tcW = dimensions.x/getModel().getImageWidth();
			float tcH = dimensions.y/getModel().getImageHeight();
			getModel().updateTiledTexCoords(0, -tcH, tcW, 0);
		}
		geom.updateModelBound();
		if (textElement != null) {
			updateTextElement();
		}
		updateClipping();
	}
	
	/**
	 * Returns a Vector2f containing the actual width and height of an Element
	 * @return float
	 */
	public Vector2f getDimensions() {
		return dimensions;
	}
	
	/**
	 * Returns the dimensions defined at the time of the Element's creation.
	 * @return 
	 */
	public Vector2f getOrgDimensions() { return this.orgDimensions; }
	
	/**
	 * Returns the actual width of an Element
	 * @return float
	 */
	public float getWidth() {
		return dimensions.x;
	}
	
	/**
	 * Returns the actual height of an Element
	 * @return float
	 */
	public float getHeight() {
		return dimensions.y;
	}
	
	/**
	 * Returns the width of an Element from screen x 0
	 * @return float
	 */
	public float getAbsoluteWidth() {
		return getAbsoluteX() + getWidth();
	}
	
	/**
	 * Returns the height of an Element from screen y 0
	 * @return float
	 */
	public float getAbsoluteHeight() {
		return getAbsoluteY() + getHeight();
	}
	
	/**
	 * Stubbed for future use.
	 */
	public void validateLayout() {
		if (getDimensions().x < 1 || getDimensions().y < 1) {
			Vector2f dim = getV2fPercentToPixels(getDimensions());
			resize(getAbsoluteX()+dim.x, getAbsoluteY()+dim.y, Element.Borders.SE);
		}
		if (getPosition().x < 1 || getPosition().y < 1) {
			Vector2f pos = getV2fPercentToPixels(getPosition());
			setPosition(pos.x,pos.y);
		}
		if (getElementParent() != null)
			setY(getElementParent().getHeight()-getHeight()-getY());
		else
			setY(screen.getHeight()-getHeight()-getY());
		
		for (Element el : elementChildren.values()) {
			el.validateLayout();
		}
	}
	
	/**
	 * Stubbed for future use.
	 */
	public void setInitialized() {
		this.initialized = true;
	}
	
	/**
	 * Stubbed for future use.
	 */
	public boolean getInitialized() { return this.initialized; }
	
	/**
	 * The preferred method for resizing Elements if the resize must effect nested
	 * Elements as well.
	 * 
	 * @param x the absolute x coordinate from screen x 0
	 * @param y the absolute y coordinate from screen y 0
	 * @param dir The Element.Borders used to determine the direction of the resize event
	 */
	public void resize(float x, float y, Borders dir) {
		float prevWidth = getWidth();
		float prevHeight = getHeight();
		float oX = x, oY = y;
		if (getElementParent() != null) { x -= getAbsoluteX()-getX(); }
		if (getElementParent() != null) { y -= getAbsoluteY()-getY(); }
		float nextX, nextY;
		if (dir == Borders.NW) {
			if (getLockToParentBounds()) { if (x <= 0) { x = 0; } }
			if (minDimensions != null) {
				if (getX()+getWidth()-x <= minDimensions.x) { x = getX()+getWidth()-minDimensions.x; }
			}
			if (resizeW) {
				setWidth(getX()+getWidth()-x);
				setX(x);
			}
			if (getLockToParentBounds()) { if (y <= 0) { y = 0; } }
			if (minDimensions != null) {
				if (getY()+getHeight()-y <= minDimensions.y) { y = getY()+getHeight()-minDimensions.y; }
			}
			if (resizeN) {
				setHeight(getY()+getHeight()-y);
				setY(y);
			}
		} else if (dir == Borders.N) {
			if (getLockToParentBounds()) { if (y <= 0) { y = 0; } }
			if (minDimensions != null) {
				if (getY()+getHeight()-y <= minDimensions.y) { y = getY()+getHeight()-minDimensions.y; }
			}
			if (resizeN) {
				setHeight(getY()+getHeight()-y);
				setY(y);
			}
		} else if (dir == Borders.NE) {
			nextX = oX-getAbsoluteX();
			if (getLockToParentBounds()) {
				float checkWidth = (getElementParent() == null) ? screen.getWidth() : getElementParent().getWidth();
				if (nextX >= checkWidth-getX()) {
					nextX = checkWidth-getX();
				}
			}
			if (minDimensions != null) {
				if (nextX <= minDimensions.x) { nextX = minDimensions.x; }
			}
			if (resizeE) {
				setWidth(nextX);
			}
			if (getLockToParentBounds()) { if (y <= 0) { y = 0; } }
			if (minDimensions != null) {
				if (getY()+getHeight()-y <= minDimensions.y) { y = getY()+getHeight()-minDimensions.y; }
			}
			if (resizeN) {
				setHeight(getY()+getHeight()-y);
				setY(y);
			}
		} else if (dir == Borders.W) {
			if (getLockToParentBounds()) { if (x <= 0) { x = 0; } }
			if (minDimensions != null) {
				if (getX()+getWidth()-x <= minDimensions.x) { x = getX()+getWidth()-minDimensions.x; }
			}
			if (resizeW) {
				setWidth(getX()+getWidth()-x);
				setX(x);
			}
		} else if (dir == Borders.E) {
			nextX = oX-getAbsoluteX();
			if (getLockToParentBounds()) {
				float checkWidth = (getElementParent() == null) ? screen.getWidth() : getElementParent().getWidth();
				if (nextX >= checkWidth-getX()) {
					nextX = checkWidth-getX();
				}
			}
			if (minDimensions != null) {
				if (nextX <= minDimensions.x) { nextX = minDimensions.x; }
			}
			if (resizeE) {
				setWidth(nextX);
			}
		} else if (dir == Borders.SW) {
			if (getLockToParentBounds()) { if (x <= 0) { x = 0; } }
			if (minDimensions != null) {
				if (getX()+getWidth()-x <= minDimensions.x) { x = getX()+getWidth()-minDimensions.x; }
			}
			if (resizeW) {
				setWidth(getX()+getWidth()-x);
				setX(x);
			}
			nextY = oY-getAbsoluteY();
			if (getLockToParentBounds()) {
				float checkHeight = (getElementParent() == null) ? screen.getHeight() : getElementParent().getHeight();
				if (nextY >= checkHeight-getY()) {
					nextY = checkHeight-getY();
				}
			}
			if (minDimensions != null) {
				if (nextY <= minDimensions.y) { nextY = minDimensions.y; }
			}
			if (resizeS) {
				setHeight(nextY);
			}
		} else if (dir == Borders.S) {
			nextY = oY-getAbsoluteY();
			if (getLockToParentBounds()) {
				float checkHeight = (getElementParent() == null) ? screen.getHeight() : getElementParent().getHeight();
				if (nextY >= checkHeight-getY()) {
					nextY = checkHeight-getY();
				}
			}
			if (minDimensions != null) {
				if (nextY <= minDimensions.y) { nextY = minDimensions.y; }
			}
			if (resizeS) {
				setHeight(nextY);
			}
		} else if (dir == Borders.SE) {
			nextX = oX-getAbsoluteX();
			if (getLockToParentBounds()) {
				float checkWidth = (getElementParent() == null) ? screen.getWidth() : getElementParent().getWidth();
				if (nextX >= checkWidth-getX()) {
					nextX = checkWidth-getX();
				}
			}
			if (minDimensions != null) {
				if (nextX <= minDimensions.x) { nextX = minDimensions.x; }
			}
			if (resizeE) {
				setWidth(nextX);
			}
			nextY = oY-getAbsoluteY();
			if (getLockToParentBounds()) {
				float checkHeight = (getElementParent() == null) ? screen.getHeight() : getElementParent().getHeight();
				if (nextY >= checkHeight-getY()) {
					nextY = checkHeight-getY();
				}
			}
			if (minDimensions != null) {
				if (nextY <= minDimensions.y) { nextY = minDimensions.y; }
			}
			if (resizeS) {
				setHeight(nextY);
			}
		}
		float diffX = prevWidth-getWidth();
		float diffY = prevHeight-getHeight();
		controlResizeHook();
		for (Element el : elementChildren.values()) {
			el.childResize(diffX,diffY,dir);
			el.controlResizeHook();
		}
	}
	
	// Below are different ideas for implementing minimum dimensions.  This will be cleaned up once I decide on the best approach.
	/*
	private void childResize(float diffX, float diffY, Borders dir) {
		if (dir == Borders.N) {
		//	if (dockS && scaleNS) {
		//		if (minDimensions == null) {
		//			setHeight(getHeight()-diffY);
		//		} else if (getHeight()-diffY > minDimensions.y) {
		//			setHeight(getHeight()-diffY);
		//		} else {
		//			setHeight(minDimensions.y);
		//		}
		//	}
		} else if (dir == Borders.S) {
			if (dockS && scaleNS) {
				if (minDimensions == null) {
					setHeight(getHeight()-diffY);
				} else {
					float cY = getElementParent().getHeight()-(getElementParent().getHeight()-orgPosition.y);
					if (getY() < cY) setHeight(minDimensions.y);
					else setHeight(getHeight()-diffY);
					if (getHeight() > minDimensions.y) setY(cY);
					else setY(getY()-diffY);
				}
			} else
				setY(getY()-diffY);
		}
		if (dir == Borders.W) {
			if (dockE) {
				
			}
		} else if (dir == Borders.E) {
			if (dockW) {
				
			}
		}
		
		Set<String> keys = elementChildren.keySet();
		for (String key : keys) {
			elementChildren.get(key).childResize(diffX,diffY,dir);
		}
	}
	*/
	/*
	private void childResize(float diffX, float diffY, Borders dir) {
		if (getElementParent().getDimensions().y > getElementParent().getOrgDimensions().y) {
			if (dir == Borders.NW || dir == Borders.N || dir == Borders.NE) {
				if (getScaleNS()) { if (getHeight()-diffY > minDimensions.y) { setHeight(getHeight()-diffY); } }
			//	if (getScaleNS()) setHeight(getHeight()-diffY);
				if (getDockN() && !getScaleNS()) setY(getY()-diffY);
			} else if (dir == Borders.SW || dir == Borders.S || dir == Borders.SE) {
				if (getScaleNS()) { if (getHeight()-diffY > minDimensions.y) { setHeight(getHeight()-diffY); } }
			//	if (getScaleNS()) setHeight(getHeight()-diffY);
				if (getDockN() && !getScaleNS()) setY(getY()-diffY);
			}
		} else {
			setHeight(getOrgDimensions().y);
			if (getDockN() && !getScaleNS()) setY(getY()-diffY);
		}
		if (getElementParent().getDimensions().x > getElementParent().getOrgDimensions().x) {
			if (dir == Borders.NW || dir == Borders.W || dir == Borders.SW) {
				if (getScaleEW()) { if (getWidth()-diffX > minDimensions.x) { setWidth(getWidth()-diffX); } }
			//	if (getScaleEW()) setWidth(getWidth()-diffX);
				if (getDockE() && !getScaleEW()) setX(getX()-diffX);
			} else if (dir == Borders.NE || dir == Borders.E || dir == Borders.SE) {
				if (getScaleEW()) { if (getWidth()-diffX > minDimensions.x) { setWidth(getWidth()-diffX); }  }
			//	if (getScaleEW()) setWidth(getWidth()-diffX);
				if (getDockE() && !getScaleEW()) setX(getX()-diffX);
			}
		} else {
			setWidth(getOrgDimensions().x);
			if (getDockE() && !getScaleEW()) setX(getX()-diffX);
		}
		for (Element el : elementChildren.values()) {
			el.childResize(diffX,diffY,dir);
			el.controlResizeHook();
		}
	}
	*/
	private void childResize(float diffX, float diffY, Borders dir) {
		if (dir == Borders.NW || dir == Borders.N || dir == Borders.NE) {
			if (getScaleNS()) setHeight(getHeight()-diffY);
			if ((getDocking() == Docking.NW || getDocking() == Docking.NE) && !getScaleNS()) setY(getY()-diffY);
		} else if (dir == Borders.SW || dir == Borders.S || dir == Borders.SE) {
			if (getScaleNS()) setHeight(getHeight()-diffY);
			if ((getDocking() == Docking.NW || getDocking() == Docking.NE) && !getScaleNS()) setY(getY()-diffY);
		}
		if (dir == Borders.NW || dir == Borders.W || dir == Borders.SW) {
			if (getScaleEW()) setWidth(getWidth()-diffX);
			if ((getDocking() == Docking.NE || getDocking() == Docking.SE) && !getScaleEW()) setX(getX()-diffX);
		} else if (dir == Borders.NE || dir == Borders.E || dir == Borders.SE) {
			if (getScaleEW()) setWidth(getWidth()-diffX);
			if ((getDocking() == Docking.NE || getDocking() == Docking.SE) && !getScaleEW()) setX(getX()-diffX);
		}
		for (Element el : elementChildren.values()) {
			el.childResize(diffX,diffY,dir);
			el.controlResizeHook();
		}
	}
	/*
	private void childResize(float diffX, float diffY, Borders dir) {
		if (getUID().equals("FirstName")) { System.out.println(orgRelDimensions); }
		if (dir == Borders.NW || dir == Borders.N || dir == Borders.NE) {
			if (getElementParent().getHeight()*orgRelDimensions.y > minDimensions.y) {
				if (getScaleNS()) setHeight(getElementParent().getHeight()*orgRelDimensions.y);//getHeight()-diffY);
			} else {
				if (getScaleNS()) setHeight(orgDimensions.y);
			}
			if (getDockN() && !getScaleNS()) setY(getY()-diffY);
		} else if (dir == Borders.SW || dir == Borders.S || dir == Borders.SE) {
			if (getElementParent().getHeight()*orgRelDimensions.y > minDimensions.y) {
				if (getScaleNS()) setHeight(getElementParent().getHeight()*orgRelDimensions.y);//getHeight()-diffY);
			} else {
				if (getScaleNS()) setHeight(orgDimensions.y);
			}
			if (getDockN() && !getScaleNS()) setY(getY()-diffY);
		}
		if (dir == Borders.NW || dir == Borders.W || dir == Borders.SW) {
			if (getElementParent().getWidth()*orgRelDimensions.x > minDimensions.x) {
				if (getScaleEW()) setWidth(getElementParent().getWidth()*orgRelDimensions.x);//getWidth()-diffX);
			} else {
				if (getScaleEW()) setWidth(orgDimensions.x);
			}
			if (getDockE() && !getScaleEW()) setX(getX()-diffX);
		} else if (dir == Borders.NE || dir == Borders.E || dir == Borders.SE) {
			if (getElementParent().getWidth()*orgRelDimensions.x > minDimensions.x) {
				if (getScaleEW()) setWidth(getElementParent().getWidth()*orgRelDimensions.x);//getWidth()-diffX);
			} else {
				if (getScaleEW()) setWidth(orgDimensions.x);
			}
			if (getDockE() && !getScaleEW()) setX(getX()-diffX);
		}
		for (Element el : elementChildren.values()) {
			el.childResize(diffX,diffY,dir);
			el.controlResizeHook();
		}
	}
	*/
	/**
	 * Overridable method for extending the resize event
	 */
	public void controlResizeHook() {
		
	}
	
	public void sizeToContent() {
		float innerX = 10000, innerY = 10000, innerW = -10000, innerH = -10000;
		float currentHeight = getHeight();
		Map<Element,Float> newY = new HashMap();
		for (Element child : elementChildren.values()) {
			float x = child.getX();
			float y = currentHeight-(child.getY()+child.getHeight());
			float w = child.getX()+child.getWidth();
			float h = currentHeight-child.getY();
			if (x < innerX) innerX = x;
			if (y < innerY) innerY = y;
			if (w > innerW) innerW = w;
			if (h > innerH) innerH = h;
			newY.put(child, h);
		}
		this.setDimensions(innerW+innerX, innerH+innerY);
		for (Element child : elementChildren.values()) {
			float diff = newY.get(child);
			child.setY(innerH-(diff-innerY));
		}
	}
	/**
	 * Moves the Element to the specified coordinates
	 * @param x The new x screen coordinate of the Element
	 * @param y The new y screen coordinate of the Element
	 */
	public void moveTo(float x, float y) {
		if (getLockToParentBounds()) {
			if (x < 0) { x = 0; }
			if (y < 0) { y = 0; }
			if (getElementParent() != null) {
				if (x > getElementParent().getWidth()-getWidth()) {
					x = getElementParent().getWidth()-getWidth();
				}
				if (y > getElementParent().getHeight()-getHeight()) {
					y = getElementParent().getHeight()-getHeight();
				}
			} else {
				if (x > screen.getWidth()-getWidth()) {
					x = screen.getWidth()-getWidth();
				}
				if (y > screen.getHeight()-getHeight()) {
					y = screen.getHeight()-getHeight();
				}
			}
		}
		setX(x);
		setY(y);
		controlMoveHook();
	}
	
	/**
	 * Overridable method for extending the move event
	 */
	public void controlMoveHook() {
		
	}
	
	/**
	 * Centers the Element to it's parent Element.  If the parent element is null, it will use the screen's width/height.
	 */
	public void centerToParent() {
		if (elementParent == null) {
			setPosition(screen.getWidth()/2-(getWidth()/2),screen.getHeight()/2-(getHeight()/2));
		} else {
			setPosition(elementParent.getWidth()/2-(getWidth()/2),elementParent.getHeight()/2-(getHeight()/2));
		}
	}
	
	/**
	 * Set the north, west, east and south borders in number of pixels
	 * @param borderSize 
	 */
	public void setResizeBorders(float borderSize) {
		borders.set(borderSize,borderSize,borderSize,borderSize);
	}
	
	/**
	 * Set the north, west, east and south borders in number of pixels
	 * @param nBorder float
	 * @param wBorder float
	 * @param eBorder float
	 * @param sBorder float
	 */
	public void setResizeBorders(float nBorder, float wBorder, float eBorder, float sBorder) {
		borders.setX(nBorder);
		borders.setY(wBorder);
		borders.setZ(eBorder);
		borders.setW(sBorder);
	}
	
	/**
	 * Sets the width of north border in number of pixels
	 * @param nBorder float
	 */
	public void setNorthResizeBorder(float nBorder) {
		borders.setX(nBorder);
	}
	
	/**
	 * Sets the width of west border in number of pixels
	 * @param wBorder float
	 */
	public void setWestResizeBorder(float wBorder) {
		borders.setY(wBorder);
	}
	
	/**
	 * Sets the width of east border in number of pixels
	 * @param eBorder float
	 */
	public void setEastResizeBorder(float eBorder) {
		borders.setZ(eBorder);
	}
	
	/**
	 * Sets the width of south border in number of pixels
	 * @param sBorder float
	 */
	public void setSouthResizeBorder(float sBorder) {
		borders.setW(sBorder);
	}
	
	/**
	 * Returns the height of the north resize border
	 * 
	 * @return float
	 */
	public float getResizeBorderNorthSize() {
		return this.borderHandles.x;
	}
	
	/**
	 * Returns the width of the west resize border
	 * 
	 * @return float
	 */
	public float getResizeBorderWestSize() {
		return this.borderHandles.y;
	}
	
	/**
	 * Returns the width of the east resize border
	 * 
	 * @return float
	 */
	public float getResizeBorderEastSize() {
		return this.borderHandles.z;
	}
	
	/**
	 * Returns the height of the south resize border
	 * 
	 * @return float
	 */
	public float getResizeBorderSouthSize() {
		return this.borderHandles.w;
	}
	
	/**
	 * Returns the default material for the element
	 * @param mat 
	 */
	public void setElementMaterial(Material mat) {
		this.mat = mat;
	}
	
	/**
	 * Returns a pointer to the Material used for rendering this Element.
	 * 
	 * @return Material mat
	 */
	public Material getElementMaterial() {
		return this.mat;
	}
	
	/**
	 * Returns the default Texture for the Element
	 * 
	 * @return Texture defaultTexture
	 */
	public Texture getElementTexture() {
		return this.defaultTex;
	}
	
	/**
	 * Returns a pointer to the custom mesh used to render the Element.
	 * 
	 * @return ElementGridQuad model
	 */
	public ElementQuadGrid getModel() {
		return this.model;
	}
	
	/**
	 * Returns the Element's Geometry.
	 * @return 
	 */
	public Geometry getGeometry() {
		return this.geom;
	}
	
	// Font & text
	/**
	 * Sets the element's text layer font
	 * @param fontPath String The font asset path
	 */
	public void setFont(String fontPath) {
		font = app.getAssetManager().loadFont(fontPath);
		if (textElement != null) {
			String text = this.getText();
			textElement.removeFromParent();
			textElement = null;
			setText(text);
		}
	}
	
	/**
	 * Returns the Bitmapfont used by the element's text layer
	 * @return BitmapFont font
	 */
	public BitmapFont getFont() {
		return this.font;
	}
	
	/**
	 * Sets the element's text layer font size
	 * @param fontSize float The size to set the font to
	 */
	public void setFontSize(float fontSize) {
		this.fontSize = fontSize;
		if (textElement != null) {
			textElement.setSize(fontSize);
		}
	}
	
	/**
	 * Returns the element's text layer font size
	 * @return float fontSize
	 */
	public float getFontSize() {
		return this.fontSize;
	}
	
	/**
	 * Sets the element's text layer font color
	 * @param fontColor ColorRGBA The color to set the font to
	 */
	public void setFontColor(ColorRGBA fontColor) {
		this.fontColor = fontColor;
		if (textElement != null) {
			textElement.setColor(fontColor);
		}
	}
	
	/**
	 * Return the element's text layer font color
	 * @return ColorRGBA fontColor
	 */
	public ColorRGBA getFontColor() {
		return this.fontColor;
	}
	
	/**
	 * Sets the element's text layer horizontal alignment
	 * 
	 * @param textAlign 
	 */
	public void setTextAlign(BitmapFont.Align textAlign) {
		this.textAlign = textAlign;
		if (textElement != null) {
			textElement.setAlignment(textAlign);
		}
	}
	
	/**
	 * Returns the element's text layer horizontal alignment
	 * @return Align text Align
	 */
	public BitmapFont.Align getTextAlign() {
		return this.textAlign;
	}
	
	/**
	 * Sets the element's text layer vertical alignment
	 * 
	 * @param textVAlign 
	 */
	public void setTextVAlign(BitmapFont.VAlign textVAlign) {
		this.textVAlign = textVAlign;
		if (textElement != null) {
			textElement.setVerticalAlignment(textVAlign);
		}
	}
	
	/**
	 * Returns the element's text layer vertical alignment
	 * @return VAlign textVAlign
	 */
	public BitmapFont.VAlign getTextVAlign() {
		return this.textVAlign;
	}
	
	/**
	 * Sets the element's text later wrap mode
	 * @param textWrap LineWrapMode textWrap
	 */
	public void setTextWrap(LineWrapMode textWrap) {
		this.textWrap = textWrap;
		if (textElement != null) {
			textElement.setLineWrapMode(textWrap);
		}
	}
	
	/**
	 * Returns the element's text layer wrap mode
	 * @return LineWrapMode textWrap
	 */
	public LineWrapMode getTextWrap() {
		return this.textWrap;
	}
	
	/**
	 * Sets the elements text layer position
	 * @param x Position's x coord
	 * @param y Position's y coord
	 */
	public void setTextPosition(float x, float y) {
		this.textPosition = new Vector2f(x,y);
	}
	
	/**
	 * Returns the current x, y coords of the element's text layer
	 * @return Vector2f textPosition
	 */
	public Vector2f getTextPosition() {
		return this.textPosition;
	}
	
	/**
	 * Sets the padding set for the element's text layer
	 * @param textPadding 
	 */
	public void setTextPadding(float textPadding) {
		this.textPadding = textPadding;
	}
	
	/**
	 * Returns the ammount of padding set for the elements text layer
	 * @return float textPadding
	 */
	public float getTextPadding() {
		return this.textPadding;
	}
	
	/**
	 * Updates the element's textlayer position and boundary
	 */
	protected void updateTextElement() {
		if (textElement != null) {
			textElement.setLocalTranslation(textPosition.x+textPadding, getHeight()-(textPosition.y+textPadding), textElement.getLocalTranslation().z);
			textElement.setBox(new Rectangle(0,0,dimensions.x-(textPadding*2),dimensions.y-(textPadding*2)));
		//	textElement.setPosition(textPadding, textPadding);
		//	textElement.setDimensions(dimensions.x-(textPadding*2),dimensions.y-(textPadding*2));
		//	textElement.controlResizeHook();
		//	textElement.updateAnimText(0);
		}
	}
	
	/**
	 * Sets the text of the element.
	 * @param text String The text to display.
	 */
	public void setText(String text) {
		this.text = text;
		if (textElement == null) {
			textElement = new BitmapText(font, false);
			textElement.setBox(new Rectangle(0,0,dimensions.x,dimensions.y));
		//	textElement = new TextElement(screen, Vector2f.ZERO, getDimensions());
		}
		textElement.setLineWrapMode(textWrap);
		textElement.setAlignment(textAlign);
		textElement.setVerticalAlignment(textVAlign);
		textElement.setSize(fontSize);
		textElement.setColor(fontColor);
		textElement.setText(text);
		updateTextElement();
		if (textElement.getParent() == null) {
			this.attachChild(textElement);
		}
	}
	
	/**
	 * Retuns the current visible text of the element.
	 * @return String text
	 */
	public String getText() {
		return this.text;
	}
	
	/**
	 * Returns a pointer to the BitmapText element of this Element.  Returns null
	 * if setText() has not been called.
	 * 
	 * @return BitmapText textElement
	 */
	public BitmapText getTextElement() {
//	public TextElement getTextElement() {
		return this.textElement;
	}
	
	// Clipping
	/**
	 * Adds an alpha map to the Elements material
	 * @param alphaMap A String path to the alpha map
	 */
	public void setAlphaMap(String alphaMap) {
		Texture alpha = null;
		if (screen.getUseTextureAtlas()) {
			if (this.getElementTexture() != null)	alpha = getElementTexture();
			else									alpha = screen.getAtlasTexture();
			Vector2f alphaOffset = getAtlasTextureOffset(screen.parseAtlasCoords(alphaMap));
			mat.setVector2("OffsetAlphaTexCoord", alphaOffset);
		} else {
			alpha = app.getAssetManager().loadTexture(alphaMap);
			alpha.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
			alpha.setMagFilter(Texture.MagFilter.Nearest);
			alpha.setWrap(Texture.WrapMode.Clamp);
		}
		
		this.alphaMap = alpha;
		
		if (defaultTex == null) {
			if (!screen.getUseTextureAtlas()) {
				float imgWidth = alpha.getImage().getWidth();
				float imgHeight = alpha.getImage().getHeight();
				float pixelWidth = 1f/imgWidth;
				float pixelHeight = 1f/imgHeight;

				this.model = new ElementQuadGrid(this.dimensions, borders, imgWidth, imgHeight, pixelWidth, pixelHeight, 0, 0, imgWidth, imgHeight);

				geom.setMesh(model);
			} else {
				float[] coords = screen.parseAtlasCoords(alphaMap);
				float textureAtlasX = coords[0];
				float textureAtlasY = coords[1];
				float textureAtlasW = coords[2];
				float textureAtlasH = coords[3];
				
				float imgWidth = alpha.getImage().getWidth();
				float imgHeight = alpha.getImage().getHeight();
				float pixelWidth = 1f/imgWidth;
				float pixelHeight = 1f/imgHeight;
				
				textureAtlasY = imgHeight-textureAtlasY-textureAtlasH;
				
				model = new ElementQuadGrid(this.getDimensions(), borders, imgWidth, imgHeight, pixelWidth, pixelHeight, textureAtlasX, textureAtlasY, textureAtlasW, textureAtlasH);
				
				geom.setMesh(model);
				mat.setVector2("OffsetAlphaTexCoord", new Vector2f(0,0));
			}
		}
		mat.setTexture("AlphaMap", alpha);
	}
	
	public Texture getAlphaMap() {
		return this.alphaMap;
	}
	
	public void setColorMap(String colorMap) {
		Texture color = null;
		if (screen.getUseTextureAtlas()) {
			if (this.getElementTexture() != null)	color = getElementTexture();
			else									color = screen.getAtlasTexture();
		} else {
			color = app.getAssetManager().loadTexture(colorMap);
			color.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
			color.setMagFilter(Texture.MagFilter.Nearest);
			color.setWrap(Texture.WrapMode.Clamp);
		}
		
		this.defaultTex = color;
		
		if (!screen.getUseTextureAtlas()) {
			float imgWidth = color.getImage().getWidth();
			float imgHeight = color.getImage().getHeight();
			float pixelWidth = 1f/imgWidth;
			float pixelHeight = 1f/imgHeight;

			this.model = new ElementQuadGrid(this.dimensions, borders, imgWidth, imgHeight, pixelWidth, pixelHeight, 0, 0, imgWidth, imgHeight);

			geom.setMesh(model);
		} else {
			float[] coords = screen.parseAtlasCoords(colorMap);
			float textureAtlasX = coords[0];
			float textureAtlasY = coords[1];
			float textureAtlasW = coords[2];
			float textureAtlasH = coords[3];

			float imgWidth = color.getImage().getWidth();
			float imgHeight = color.getImage().getHeight();
			float pixelWidth = 1f/imgWidth;
			float pixelHeight = 1f/imgHeight;

			textureAtlasY = imgHeight-textureAtlasY-textureAtlasH;

			model = new ElementQuadGrid(this.getDimensions(), borders, imgWidth, imgHeight, pixelWidth, pixelHeight, textureAtlasX, textureAtlasY, textureAtlasW, textureAtlasH);

			geom.setMesh(model);
		}
		
		mat.setTexture("ColorMap", color);
		mat.setColor("Color", ColorRGBA.White);
	}
	
	/**
	 * This may be remove soon and probably should not be used as the method of handling
	 * hide show was updated making this unnecissary.
	 * 
	 * @param wasVisible boolean
	 */
	public void setDefaultWasVisible(boolean wasVisible) {
		this.wasVisible = wasVisible;
	}
	
	/**
	 * Shows the current Element with the defined Show effect.  If no Show effect is defined, the Element will show as normal.
	 */
	public void showWithEffect() {
		Effect effect = getEffect(Effect.EffectEvent.Show);
		if (effect != null) {
			if (effect.getEffectType() == Effect.EffectType.FadeIn) {
				Effect clone = effect.clone();
				clone.setAudioFile(null);
				this.propagateEffect(clone, false);
			} else {
				if (getTextElement() != null)
					getTextElement().setAlpha(1f);
				screen.getEffectManager().applyEffect(effect);
			}
		} else
			this.show();
	}
	
	/**
	 * Sets this Element and any Element contained within it's nesting order to visible.
	 * 
	 * NOTE: Hide and Show relies on shader-based clipping
	 */
	public void show() {
		if (!isVisible) {
			screen.updateZOrder(getAbsoluteParent());
			this.isVisible = true;
			this.isClipped = wasClipped;
			updateClipping();
			controlShowHook();
			
			if (getTextElement() != null)
				getTextElement().setAlpha(1f);
			
			if (getParent() == null) {
				if (getElementParent() != null) {
					getElementParent().attachChild(this);
				} else {
					screen.getGUINode().attachChild(this);
				}
			}
			
			for (Element el : elementChildren.values()) {
				el.childShow();
			}
		}
	}
	
	public void showAsModal(boolean showWithEffect) {
		isVisibleAsModal = true;
		screen.showAsModal(this,showWithEffect);
	}
	/**
	 * Recursive call for properly showing children of the Element.  I'm thinking this
	 * this needs to be a private method, however I need to verify this before I
	 * update it.
	 */
	public void childShow() {
		if (getTextElement() != null)
			getTextElement().setAlpha(1f);
		
		this.isVisible = wasVisible;
		this.isClipped = wasClipped;
		updateClipping();
		controlShowHook();
		for (Element el : elementChildren.values()) {
			el.childShow();
		}
	}
	
	/**
	 * An overridable method for extending the show event.
	 */
	public void controlShowHook() {  }
	
	/**
	 * Hides the element using the current defined Hide effect.  If no Hide effect is defined, the Element will hide as usual.
	 */
	public void hideWithEffect() {
		Effect effect = getEffect(Effect.EffectEvent.Hide);
		if (effect != null) {
			if (effect.getEffectType() == Effect.EffectType.FadeOut) {
				Effect clone = effect.clone();
				clone.setAudioFile(null);
				this.propagateEffect(clone, true);
			} else
				screen.getEffectManager().applyEffect(effect);
			if (isVisibleAsModal) {
				isVisibleAsModal = false;
				screen.hideModalBackground();
			}
		} else
			this.hide();
	}
	
	/**
	 * Recursive call that sets this Element and any Element contained within it's 
	 * nesting order to hidden.
	 * 
	 * NOTE: Hide and Show relies on shader-based clipping
	 */
	public void hide() {
		if (isVisible) {
			if (isVisibleAsModal) {
				isVisibleAsModal = false;
				screen.hideModalBackground();
			}
			this.wasVisible = isVisible;
			this.isVisible = false;
			this.isClipped = true;
		}
		updateClipping();
		controlHideHook();
		removeFromParent();
		for (Element el : elementChildren.values()) {
			el.childHide();
		}
	}
	
	/**
	 * For internal use.  This method should never be called directly.
	 */
	public void childHide() {
		if (isVisible) {
			this.wasVisible = isVisible;
			this.isVisible = false;
			this.isClipped = true;
		}
		updateClipping();
		controlHideHook();
		for (Element el : elementChildren.values()) {
			el.childHide();
		}
	}
	
	/**
	 * Hides or shows the element (true = show, false = hide)
	 * @param visibleState 
	 */
	public void setIsVisible(boolean visibleState) {
		if (visibleState) {
			show();
		} else {
			hide();
		}
	}
	
	/**
	 * Toggles the Element's visibility based on the current state.
	 */
	public void setIsVisible() {
		if (getIsVisible())	hide();
		else				show();
	}
	
	/**
	 * An overridable method for extending the hide event.
	 */
	public void controlHideHook() {  }
	
	public void cleanup() {
		controlCleanupHook();
		for (Element el : elementChildren.values()) {
			el.cleanup();
		}
	}
	/**
	 * An overridable method for handling control specific cleanup.
	 */
	public void controlCleanupHook() {  }
	
	/**
	 * For internal use only - DO NOT CALL THIS METHOD
	 * @param effect Effect
	 * @param callHide boolean
	 */
	public void propagateEffect(Effect effect, boolean callHide) {
		Effect nEffect = effect.clone();
		nEffect.setCallHide(callHide);
		nEffect.setElement(this);
		screen.getEffectManager().applyEffect(nEffect);
		for (Element el : elementChildren.values()) {
			el.propagateEffect(effect, false);
		}
	}
	
	/**
	 * Return if the Element is visible
	 * 
	 * @return boolean isVisible
	 */
	public boolean getIsVisible() {
		return this.isVisible;
	}
	
	/**
	 * Sets the elements clipping layer to the provided element.
	 * @param clippingLayer The element that provides the clipping boundaries.
	 */
	public void setClippingLayer(Element clippingLayer) {
		if (clippingLayer != null) {
			this.isClipped = true;
			this.wasClipped = true;
			this.clippingLayer = clippingLayer;
			this.mat.setBoolean("UseClipping", true);
		} else {
			this.isClipped = false;
			this.wasClipped = false;
			this.clippingLayer = null;
			this.mat.setBoolean("UseClipping", false);
		}
	}
	
	/**
	 * Recursive update of all child Elements clipping layer
	 * @param clippingLayer The clipping layer to apply
	 */
	public void setControlClippingLayer(Element clippingLayer) {
		setClippingLayer(clippingLayer);
		for (Element el : elementChildren.values()) {
			el.setControlClippingLayer(clippingLayer);
		}
	}
	
	/**
	 * Returns if the Element's clipping layer has been set
	 * 
	 * @return boolean isClipped
	 */
	public boolean getIsClipped() {
		return isClipped;
	}
	
	/**
	 * Returns the elements clipping layer or null is element doesn't use clipping
	 * @return Element clippingLayer
	 */
	public Element getClippingLayer() {
		return this.clippingLayer;
	}
	
	/**
	 * Returns a Vector4f containing the current boundaries of the element's clipping layer
	 * @return Vector4f clippingBounds
	 */
	public Vector4f getClippingBounds() {
		return this.clippingBounds;
	}
	
	/**
	 * Adds a padding to the clippinglayer, in effect this contracts the size of the clipping
	 * bounds by the specified number of pixels
	 * @param clipPadding The number of pixels to pad the clipping area
	 */
	public void setClipPadding(float clipPadding) {
		this.clipPadding = clipPadding;
	}
	
	/**
	 * Returns the current clipPadding
	 * @return float clipPadding
	 */
	public float getClipPadding() {
		return clipPadding;
	}
	
	/**
	 * Updates the clipping bounds for any element that has a clipping layer
	 * 
	 * See updateLocalClipping
	 */
	public void updateClipping() {
		updateLocalClipping();
		for (Element el : elementChildren.values()) {
			el.updateClipping();
		}
	}
	
	/**
	 * Updates the clipping bounds for any element that has a clipping layer
	 */
	public void updateLocalClipping() {
		if (isVisible) {
			if (clippingLayer != null) {
				float cPadding = 0;
				if (clippingLayer != this)
					cPadding = clippingLayer.getClipPadding();
				clippingBounds.set(
					clippingLayer.getAbsoluteX()+cPadding,
					clippingLayer.getAbsoluteY()+cPadding,
					clippingLayer.getAbsoluteWidth()-cPadding,
					clippingLayer.getAbsoluteHeight()-cPadding
				);
				mat.setVector4("Clipping", clippingBounds);
				mat.setBoolean("UseClipping", true);
			} else {
				mat.setBoolean("UseClipping", false);
			}
		} else {
			clippingBounds.set(0,0,0,0);
			mat.setVector4("Clipping", clippingBounds);
			mat.setBoolean("UseClipping", true);
		}
		setFontPages();
	}
	
	/**
	 * Shrinks the clipping area by set number of pixels
	 * 
	 * @param textClipPadding The number of pixels to pad the clipping area with on each side
	 */
	public void setTextClipPadding(float textClipPadding) {
		this.textClipPadding = textClipPadding;
	}
	
	/**
	 * Updates font materials with any changes to clipping layers
	 */
	private void setFontPages() {
		if (textElement != null) {
			if (!isVisible) {
				for (int i = 0; i < font.getPageSize(); i++) {
					this.font.getPage(i).setVector4("Clipping", clippingBounds);
					this.font.getPage(i).setBoolean("UseClipping", true);
				}
			} else {
				if (isClipped) {
					for (int i = 0; i < font.getPageSize(); i++) {
						this.font.getPage(i).setVector4("Clipping", clippingBounds.add(textClipPadding, textClipPadding, -textClipPadding, -textClipPadding));
						this.font.getPage(i).setBoolean("UseClipping", true);
					}
				} else {
					for (int i = 0; i < font.getPageSize(); i++) {
						this.font.getPage(i).setBoolean("UseClipping", false);
					}
				}
			} 
		}
	}
	
	// Effects
	/**
	 * Associates an Effect with this Element.  Effects are not automatically associated
	 * with the specified event, but instead, the event type is used to retrieve the Effect
	 * at a later point
	 * 
	 * @param effectEvent The Effect.EffectEvent the Effect is to be registered with
	 * @param effect The Effect to store
	 */
	public void addEffect(Effect.EffectEvent effectEvent, Effect effect) {
		addEffect(effect);
	}
	
	/**
	 * Associates an Effect with this Element.  Effects are not automatically associated
	 * with the specified event, but instead, the event type is used to retrieve the Effect
	 * at a later point
	 * 
	 * @param effect The Effect to store
	 */
	public void addEffect(Effect effect) {
		effects.remove(effect.getEffectEvent());
		if (!effects.containsKey(effect.getEffectEvent())) {
			effect.setElement(this);
			effects.put(effect.getEffectEvent(), effect);
		}
	}
	
	/**
	 * Removes the Effect associated with the Effect.EffectEvent specified
	 * @param effectEvent 
	 */
	public void removeEffect(Effect.EffectEvent effectEvent) {
		effects.remove(effectEvent);
	}
	
	/**
	 * Retrieves the Effect associated with the specified Effect.EffectEvent
	 * 
	 * @param effectEvent
	 * @return effect
	 */
	public Effect getEffect(Effect.EffectEvent effectEvent) {
		Effect effect = null;
		if (effects.get(effectEvent) != null)
			effect = effects.get(effectEvent).clone();
		return effect;
	}
	
	/**
	 * Called by controls during construction to prepopulate effects based on Styles.
	 * 
	 * @param styleName The String identifier of the Style
	 */
	protected void populateEffects(String styleName) {
		int index = 0;
		Effect effect;
		while ((effect = screen.getStyle(styleName).getEffect("event" + index)) != null) {
			effect = effect.clone();
			effect.setElement(this);
			this.addEffect(effect);
			index++;
		}
	}
	
	/**
	 * Overrides the screen global alpha with the specified value. setIngoreGlobalAlpha must be enabled prior to calling this method.
	 * @param globalAlpha 
	 */
	public void setGlobalAlpha(float globalAlpha) {
		if (!ignoreGlobalAlpha) {
			getElementMaterial().setFloat("GlobalAlpha", globalAlpha);
			for (Element el : elementChildren.values()) {
				el.setGlobalAlpha(globalAlpha);
			}
		} else {
			getElementMaterial().setFloat("GlobalAlpha", 1);
		}
	}
	
	/**
	 * Will enable or disable the use of the screen defined global alpha setting.
	 * @param ignoreGlobalAlpha 
	 */
	public void setIgnoreGlobalAlpha(boolean ignoreGlobalAlpha) {
		this.ignoreGlobalAlpha = ignoreGlobalAlpha;
	}
	
	// Tab focus
	/**
	 * For use by the Form control (Do not call this method directly)
	 * @param form The form the Element has been added to
	 */
	public void setForm(Form form) {
		this.form = form;
	}
	
	/**
	 * Returns the form the Element is controlled by
	 * @return Form form
	 */
	public Form getForm() {
		return this.form;
	}
	
	/**
	 * Sets the tab index (This is assigned by the Form control. Do not call this method directly)
	 * @param tabIndex The tab index assigned to the Element
	 */
	public void setTabIndex(int tabIndex) {
		this.tabIndex = tabIndex;
	}
	
	/**
	 * Returns the tab index assigned by the Form control
	 * @return tabIndex
	 */
	public int getTabIndex() {
		return tabIndex;
	}
	
	// Off Screen Rendering Bridge
	public void addOSRBridge(OSRBridge bridge) {
		this.bridge = bridge;
		addControl(bridge);
		getElementMaterial().setTexture("ColorMap", bridge.getTexture());
		getElementMaterial().setColor("Color", ColorRGBA.White);
	}
	
	// Tool Tip
	/**
	 * Sets the Element's ToolTip text
	 * @param toolTip String
	 */
	public void setToolTipText(String toolTip) {
		this.toolTipText = toolTip;
	}
	
	/**
	 * Returns the Element's current ToolTip text
	 * @return String
	 */
	public String getToolTipText() {
		return toolTipText;
	}
	
	/**
	 * For internal use - DO NOT CALL THIS METHOD
	 * @param hasFocus boolean
	 */
	public void setHasFocus(boolean hasFocus) {
		this.hasFocus = hasFocus;
	}
	
	/**
	 * Returns if the Element currently has input focus
	 * @return 
	 */
	public boolean getHasFocus() {
		return this.hasFocus;
	}
	
	public void setResetKeyboardFocus(boolean resetKeyboardFocus) {
		this.resetKeyboardFocus = resetKeyboardFocus;
	}
	
	public boolean getResetKeyboardFocus() { return this.resetKeyboardFocus; }
	
	// Modal
	/**
	 * Enables standard modal mode for the Element.
	 * @param isModal 
	 */
	public void setIsModal(boolean isModal) {
		this.isModal = isModal;
	}
	
	/**
	 * Returns if the Element is currently modal
	 * @return Ret
	 */
	public boolean getIsModal() {
		return this.isModal;
	}
	
	/**
	 * For internal use - DO NOT CALL THIS METHOD
	 * @param hasFocus boolean
	 */
	public void setIsGlobalModal(boolean isGlobalModal) {
		this.isGlobalModal = isGlobalModal;
	}
	
	/**
	 * For internal use - DO NOT CALL THIS METHOD
	 * @param hasFocus boolean
	 */
	public boolean getIsGlobalModal() {
		return this.isGlobalModal;
	}
	
	// User data
	/**
	 * Stores provided data with the Element
	 * @param elementUserData Object Data to store
	 */
	public void setElementUserData(Object elementUserData) {
		this.elementUserData = elementUserData;
	}
	
	/**
	 * Returns the data stored with this Element
	 * @return Object
	 */
	public Object getElementUserData() {
		return this.elementUserData;
	}
	
	Vector2f origin = new Vector2f(0,0);
	/**
	 * Stubbed for future use
	 * @param originX
	 * @param originY 
	 */
	public void setOrigin(float originX, float originY) {
		origin.set(originX, originY);
	}
	
	/**
	 * Stubbed for future use.
	 * @return 
	 */
	public Vector2f getOrigin() {
		return this.origin;
	}
}
