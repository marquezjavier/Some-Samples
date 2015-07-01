package net.javs.dao.chapters;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.javs.dao.DAO;
import net.javs.exceptions.InternalErrorException;
import net.javs.exceptions.NotFoundException;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * Chapter
 * 
 * Right now the chapter class is only used for saving
 * 
 * Example chapter in mongo
 {
	"_id" : ObjectId("51cbfc9ef702fc2ba812fe67"),
	"dateCreated" : ISODate("2012-08-13T14:01:07Z"),
	"joins" : [
		{
			"joinType" : "primary",
			"scope" : "content",
			"_id" : ObjectId("51cbf94d7896bb431f6baa64")
		},
		{
			"joinType" : "selected",
			"scope" : "chapters",
			"_id" : ObjectId("51cbfc9ef702fc2ba812fe68")
		},
		{
			"joinType" : "primary",
			"scope" : "content",
			"_id" : ObjectId("51cbf49e7896bb431f6b0024"),
			"hide" : "1"
		},
		{
			"joinType" : "primary",
			"isLCP" : "1",
			"lcpedFrom" : "9399",
			"_id" : ObjectId("51cbf5867896bb431f6b229e"),
			"scope" : "content"
		},
		{
			"_id" : ObjectId("52546e850cf250213f33f932"),
			"isAdminOnly" : "1",
			"joinType" : "selected",
			"scope" : "chapters"
		},
		{
			"joinType" : "primary",
			"scope" : "content",
			"_id" : ObjectId("51cbf94d7896bb431f6baa5b")
		}
	],
	"name" : "Sample Chapter",
	"oldId" : "81380"
 }
 * 
 * "name"  is the name of the chapter
 * "oldId" is the legacy id that was used in the old SQL database, which is still supported for chapters that contain it
 * "joins" is how content and other chapters are join to this chapter. This is where most of the manipulation and
 * 		   we can't really filter and get things from different tables in an efficient manner so we do transformations to grab things join to chapters
 * 		   this sub document also maintains the order of the content because we can also not sort this sub document on return
 * 		   "LCP" related fields are used when content or chaptered is published somewhere other than the original book
 */

public class Chapter extends DAO {
	
	private String id;
	private String name;
	private String description;
	private String abs;
	private String shortName;
	private String galleryId;
	private String mainFeature;
	private String inactive;
	private String adminOnly;
	private String displayImage;
	private String randomFeatures;
	private String showUpdatedContent;
	private String lcpCopyOfChapter;
	private String lcpLiveUpdates;
	private String lcpPrivateCommenting;
	private String imageId;
	private String slideShowId;
	private Date lastUpdatedTime;
	
	
	public Chapter(){
		this.id = "0";
		this.name = null;
		this.description = null;
		this.abs = null;
		this.shortName = null;
		this.mainFeature= null;
		this.galleryId = null;
		this.inactive = null;
		this.adminOnly = null;
		this.displayImage= null;
		this.randomFeatures= null;
		this.showUpdatedContent= null;
		this.lcpCopyOfChapter= null;
		this.lcpLiveUpdates= null;
		this.lcpPrivateCommenting= null;
		this.imageId= null;
		this.slideShowId= null;
		this.lastUpdatedTime = null;
	}
	
	/**
	 * This save will create a new chapter if no id is set<br \>
	 * if an id is set it will update the chapter matching that Id <br \><br \>
	 * 
	 * Only updates fields with values<br \>
	 * If you do not want to update a field, Leave it as null<br \>
	 */
	@Override
	public boolean save() {
		try{
			this.initMongo();		
			BasicDBObject doc = new BasicDBObject();
			BasicDBObject removeFields = new BasicDBObject();
			
			//We only want to add or update fields that are being passed.
			//All fields that are left null are not updated or touched
			if(name != null){
				doc.append("name", name);
			}
			
			if(description != null){
				if(!description.equals("")){
					doc.append("description", description);
				}else{
					removeFields.append("description", 1);
				}
			}
			
			if(abs != null){
				if(!abs.equals("")){
					doc.append("abstract", abs);
				}else{
					removeFields.append("abstract", 1);
				}
			}
			
			if(galleryId != null){
				if(!galleryId.equals("")){
					doc.append("galleryId", galleryId);
				}else{
					removeFields.append("galleryId", 1);
				}
			}
			
			if(shortName != null){
				if(!shortName.equals("")){
					doc.append("shortName", shortName);
				}else{
					removeFields.append("shortName", 1);
				}
			}
			
			if(imageId != null){
				if(!imageId.equals("")){
					doc.append("imageId", imageId);
				}else{
					removeFields.append("imageId", 1);
				}
			}
			
			if(slideShowId != null)
				if(!slideShowId.equals(""))
					doc.append("slideShowId", slideShowId);
				else
					removeFields.append("slideShowId", 1);
			
			if(mainFeature != null){
				if(!mainFeature.equals("")){
					doc.append("mainFeature", mainFeature);
				}else{
					removeFields.append("mainFeature", 1);
				}
			}
			
			if(inactive != null){
				if(!inactive.equals("")){
					doc.append("inactive", inactive);
				}else{
					removeFields.append("inactive", 1);
				}
			}
			
			//Lets see if we need to update this chapter's joins to reflect it's adminOnly status
			Boolean isAdminOnly = false;
			Boolean updateAdminOnly = false;
			if(adminOnly != null){
				updateAdminOnly = true;
				if(!adminOnly.equals("")){
					doc.append("adminOnly", adminOnly);
					isAdminOnly = true;
				}else{
					removeFields.append("adminOnly", 1);
				}
			}
			
			if(displayImage != null){
				if(!displayImage.equals("")){
					doc.append("displayImage", displayImage);
				}else{
					removeFields.append("displayImage", 1);
				}
			}
			
			if(randomFeatures != null){
				if(!randomFeatures.equals("")){
					doc.append("randomFeatures", randomFeatures);
				}else{
					removeFields.append("randomFeatures", 1);
				}
			}
			
			if(showUpdatedContent != null){
				if(!showUpdatedContent.equals("")){
					doc.append("showUpdatedContent", showUpdatedContent);
				}else{
					removeFields.append("showUpdatedContent", 1);
				}
			}
			
			if(lcpCopyOfChapter != null){
				if(!lcpCopyOfChapter.equals("")){
					doc.append("lcpCopyOfChapter", lcpCopyOfChapter);
				}else{
					removeFields.append("lcpCopyOfChapter", 1);
				}
			}
			
			if(lcpLiveUpdates != null){
				if(!lcpLiveUpdates.equals("")){
					doc.append("lcpLiveUpdates", lcpLiveUpdates);
				}else{
					removeFields.append("lcpLiveUpdates", 1);
				}
			}
			
			if(lcpPrivateCommenting != null){
				if(!lcpPrivateCommenting.equals("")){
					doc.append("lcpPrivateCommenting", lcpPrivateCommenting);
				}else{
					removeFields.append("lcpPrivateCommenting", 1);
				}
			}
			
			DBCollection coll = db.getCollection("chapters");
			
			//Only update fields with values
			if(id != null && !id.equals("0")){
				BasicDBObject q = new BasicDBObject();
				if(ObjectId.isValid(id)){
					q.append("_id", new ObjectId(id));
				}else{
					BasicDBObject idQuery = new BasicDBObject("oldId", id);
					DBCollection idColl = db.getCollection("chapters");
					DBObject found = idColl.findOne(idQuery);	
					q.append("_id", found.get("_id"));
				}
				
				doc.append("lastUpdatedTime", new Date());
				
				BasicDBObject o = new BasicDBObject("$set", doc);
				o.append("$unset", removeFields);
				coll.update(q, o);
			}else{
				coll.insert(doc);
			}
			
			id = doc.get("_id").toString();
			
		}catch(Exception e){
			
		}
		return false;
	}
	
	//
	// Setters and getters
	//
	
	public String getId(){
		return this.id;
	}
	
	public void setId(String id){
		this.id = id;
	}
	
	public String getName(){
		return this.name;
	}
	
	public void setName(String name){
		this.name = name;
	}
	
	public String getDescription(){
		return this.description;
	}
	
	public void setDescription(String description){
		this.description = description;
	}

	public String getAbstract(){
		return this.abs;
	}
	
	public void setAbstract(String abs){
		this.abs = abs;
	}
	
	public String getShortName(){
		return this.shortName;
	}
	
	public void setShortName(String shortName){
		this.shortName = shortName;
	}
	
	public String getMainFeature(){
		return this.mainFeature;
	}
	
	public void setMainFeature(String mainFeature){
		this.mainFeature = mainFeature;
	}
	
	public String getInactive(){
		return this.inactive;
	}
	
	public void setInactive(String inactive){
		this.inactive = inactive;
	}
	
	public String getAdminOnly(){
		return this.adminOnly;
	}
	
	public void setAdminOnly(String adminOnly){
		this.adminOnly = adminOnly;
	}
	
	public String getDisplayImage(){
		return this.displayImage;
	}
	
	public void setDisplayImage(String displayImage){
		this.displayImage = displayImage;
	}
	
	public String getRandomFeatures(){
		return this.randomFeatures;
	}
	
	public void setRandomFeatures(String randomFeatures){
		this.randomFeatures = randomFeatures;
	}
	
	public String getShowUpdatedContent(){
		return this.showUpdatedContent;
	}
	
	public void setShowUpdatedContent(String showUpdatedContent){
		this.showUpdatedContent = showUpdatedContent;
	}
	
	public String getlcpLiveUpdates(){
		return this.lcpCopyOfChapter;
	}
	
	public void setLCPCopyOfChapter(String lcpCopyOfChapter){
		this.lcpCopyOfChapter = lcpCopyOfChapter;
	}
	
	public String getLCPLiveUpdates(){
		return this.lcpLiveUpdates;
	}
	
	public void setLCPLiveUpdates(String lcpLiveUpdates){
		this.lcpLiveUpdates = lcpLiveUpdates;
	}
	
	public String getLCPPrivateCommenting(){
		return this.lcpPrivateCommenting;
	}
	
	public void setLCPPrivateCommenting(String lcpPrivateCommenting){
		this.lcpPrivateCommenting = lcpPrivateCommenting;
	}
	
	public String getImageId(){
		return this.imageId;
	}
	
	public void setImageId(String imageId){
		this.imageId = imageId;
	}
	
	public String getSlideShowId(){
		return this.slideShowId;
	}
	
	public void setSlideShowId(String slideShowId){
		this.slideShowId = slideShowId;
	}
	
	public Date getLastUpdatedTime(){
		return this.lastUpdatedTime;
	}
	
	public void setLastUpdatedTime(Date LastUpdatedTime){
		this.lastUpdatedTime = lastUpdatedTime;
	}
	
	public String getGalleryId(){
		return this.galleryId;
	}
	
	public void setGalleryId(String galleryId){
		this.galleryId = galleryId;
	}
}
