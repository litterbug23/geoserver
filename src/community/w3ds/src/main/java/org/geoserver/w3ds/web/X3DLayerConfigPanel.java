/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 * 
 * @author Jorge Gustavo Rocha / Universidade do Minho
 * @author Nuno Carvalho Oliveira / Universidade do Minho
 * @author Juha Hyvärinen / Cyberlightning Ltd
 * 
 */

package org.geoserver.w3ds.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.validation.validator.MinimumValidator;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.w3ds.utilities.HashMapIModel;
import org.geoserver.w3ds.utilities.LODSetParser;
import org.geoserver.w3ds.utilities.TileSetsParser;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.data.resource.LayerEditTabPanel;
import org.geoserver.web.util.MapModel;
import org.opengis.feature.type.PropertyDescriptor;
import org.xml.sax.SAXException;

@SuppressWarnings("serial")
public class X3DLayerConfigPanel extends LayerEditTabPanel {

	CheckBox activeLayer;
	CheckBox queryable;
	DropDownChoice objectID;
	Palette objectClass;
	CheckBox tiled;
	DropDownChoice tileSets;
	CheckBox hLOD;
	DropDownChoice lodSets;
	Map<Integer, List<Double>> lodDistances;
	DropDownChoice defaultStyle;
	Palette styles;
	TextField<Double> LODdistance;

	public X3DLayerConfigPanel(String id, IModel model) throws IOException,
			ParserConfigurationException, SAXException {
		super(id, model);
		initActiveLayer(model);
		initQueryable(model);
		initObjectID(model);
		initObjectClass(model);
		initTiled(model);
		initTileSets(model);
		initHLOD(model);
		initLODSet(model);
		initLodDistances(model);
		initStyles(model);
		initDefaultStyle(model);
		/*
		 * if(!this.activeLayer.getModelObject().booleanValue())
		 * activeLayer(false);
		 */
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initActiveLayer(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		activeLayer = new CheckBox("x3dlayer", new MapModel(propertyModel, "x3d.activate")) {
			/*
			 * @Override public void onSelectionChanged(final Object
			 * newSelection) { boolean valor = this.getModelObject(); if (valor)
			 * activeLayer(true); else activeLayer(false); }
			 * 
			 * @Override protected boolean wantOnSelectionChangedNotifications()
			 * { return true; }
			 */
		};
		add(activeLayer);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initQueryable(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		queryable = new CheckBox("x3dqueryable", new MapModel(propertyModel, "x3d.queryable"));
		add(queryable);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initObjectID(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		objectID = new DropDownChoice("x3d.objectid", new MapModel(
				propertyModel, "x3d.objectid"), new AttributeNamesModel(
				new PropertyModel(model, "resource")));
		add(objectID);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initObjectClass(IModel model) {
		IModel propertiesModel = HashMapIModel.hashMap(new PropertyModel(model,
				"resource.metadata.map"), "x3d.objectclass");
		objectClass = new Palette("dclazz", propertiesModel,
				new AttributeNamesModel(new PropertyModel(model, "resource")),
				new ColNameRender(), 10, false) {
			@Override
			public Component newSelectedHeader(final String componentId) {
				return new Label(componentId, new ResourceModel(
						"X3DLayerConfigPanel.selectedHeader"));
			}

			@Override
			public Component newAvailableHeader(final String componentId) {
				return new Label(componentId, new ResourceModel(
						"X3DLayerConfigPanel.availableHeader"));
			}
		};
		add(objectClass);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initTiled(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		tiled = new CheckBox("tiled", new MapModel(propertyModel, "x3d.tiled"));
		add(tiled);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initHLOD(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		hLOD = new CheckBox("hLOD", new MapModel(propertyModel, "x3d.hLOD"));
		add(hLOD);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initTileSets(IModel model) throws IOException,
			ParserConfigurationException, SAXException {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		tileSets = new DropDownChoice("tileSets", new MapModel(propertyModel,
				"x3d.tileSet"), new CollectionModel<String>(getTileSets()));
		add(tileSets);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initLODSet(IModel model) 
	        throws IOException, ParserConfigurationException, SAXException {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		lodSets = new DropDownChoice("LODSet", new MapModel(propertyModel, "x3d.LODSet"), 
		        new CollectionModel<String>(getLODSets()));
		add(lodSets);
	}

	private void initLodDistances(IModel model) 
	        throws IOException, ParserConfigurationException, SAXException {

        // {x3d.styles=[], x3d.queryable=true, x3d.hLOD=true, x3d.tiled=false}
        PropertyModel distancePropertyModel = new PropertyModel(model, "resource.metadata");

        for (int i = 1; i < 11; i++) {
            String distance = "distance" + i;
            addLodCheckBox(model, "lod"+i);
            addLodDistance(model, distancePropertyModel, distance);
        }

	}

	private void addLodCheckBox(IModel model, String lodLevel) {
	    String lodProperty = "x3d."+lodLevel;
	    PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");
	    CheckBox lodLevelEnabled = new CheckBox(lodLevel, new MapModel(propertyModel, lodProperty));
        add(lodLevelEnabled);
	}

	private void addLodDistance(IModel model, PropertyModel distancePropertyModel, String distance) {
	    String distanceProperty = "x3d."+distance;
        TextField<Double> LODdistance = new TextField<Double>(distance, 
                new MapModel(distancePropertyModel, distanceProperty));

        LODdistance.setType(Double.class);
        LODdistance.add(new MinimumValidator<Double>(0.0));
        add(LODdistance);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initStyles(IModel model) throws IOException,
			ParserConfigurationException, SAXException {
		IModel propertiesModel = HashMapIModel.hashMap(new PropertyModel(model,
				"resource.metadata.map"), "x3d.styles");

		styles = new Palette("styles", propertiesModel, new CollectionModel<String>(getStyles()),
				new ColNameRender(), 10, false) {
			@Override
			public Component newSelectedHeader(final String componentId) {
				return new Label(componentId, new ResourceModel(
						"X3DLayerConfigPanel.selectedStyleHeader"));
			}

			@Override
			public Component newAvailableHeader(final String componentId) {
				return new Label(componentId, new ResourceModel(
						"X3DLayerConfigPanel.availableStyleHeader"));
			}
		};
		add(styles);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initDefaultStyle(IModel model) {
		PropertyModel propertyModel = new PropertyModel(model, "resource.metadata");

		defaultStyle = new DropDownChoice("x3d.defaultStyle", new MapModel(
				propertyModel, "x3d.defaultStyle"), new CollectionModel<String>(getStyles()));
		add(defaultStyle);
	}

	private List<String> getTileSets() throws IOException,
			ParserConfigurationException, SAXException {
		GeoServerResourceLoader resourceLoader = ((GeoServerApplication) getApplication())
				.getCatalog().getResourceLoader();

		File dir = resourceLoader.findOrCreateDirectory("tilesets");
		TileSetsParser tileSetsParser = new TileSetsParser(dir,
				((GeoServerApplication) getApplication()).LOGGER);
		return tileSetsParser.getTileSetsNames();
	}

	private List<String> getLODSets() throws IOException,
			ParserConfigurationException, SAXException {
		GeoServerResourceLoader resourceLoader = ((GeoServerApplication) getApplication())
				.getCatalog().getResourceLoader();

		File dir = resourceLoader.findOrCreateDirectory("lodsets");
		LODSetParser LODSetParser = new LODSetParser(dir,
				((GeoServerApplication) getApplication()).LOGGER);
		return LODSetParser.getLodSetsNames();
	}

	private List<String> getStyles() {
		List<StyleInfo> styles = new ArrayList<StyleInfo>(GeoServerApplication
				.get().getCatalog().getStyles());
		List<String> names = new ArrayList<String>();
		for (StyleInfo s : styles) {
			names.add(s.getName());
		}
		return names;
	}

	public class ColNameRender implements IChoiceRenderer {

    	public Object getDisplayValue(Object object) {
    		return (String) object;
    	}

    	public String getIdValue(Object object, int index) {
    		return (String) object;
    	}

    }

    private static class AttributeNamesModel extends LoadableDetachableModel {
    	IModel featureTypeInfo;

    	public AttributeNamesModel(IModel featureTypeInfo) {
    		this.featureTypeInfo = featureTypeInfo;
    	}

    	@Override
    	protected Object load() {
    	    //if (featureTypeInfo instanceof unsupportedClass)
	        // TODO Check if used feature type is supported by W3DS module

    		try {
    			FeatureTypeInfo fti = (FeatureTypeInfo) featureTypeInfo.getObject();
    			Collection<PropertyDescriptor> result2 = fti.getFeatureType().getDescriptors();
    			List<String> result = new ArrayList<String>();
    			for (PropertyDescriptor property : fti.getFeatureType().getDescriptors()) {
    				result.add(property.getName().getLocalPart());
    			}
    			Collections.sort(result);
    			return result;
    		} catch (IOException e) {
    			throw new RuntimeException(
    					"Could not load feature type attribute list.", e);
    		}
    	}
    }

    public class StylesModel extends LoadableDetachableModel {

		@Override
		protected Object load() {
			List<StyleInfo> styles = new ArrayList<StyleInfo>(
					GeoServerApplication.get().getCatalog().getStyles());
			return getStylesName(styles);
		}

		private List<String> getStylesName(List<StyleInfo> styles) {
			List<String> names = new ArrayList<String>();
			for (StyleInfo s : styles) {
				names.add(s.getName());
			}
			return names;
		}

	}

}
