package net.javs.dao.chapters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.javs.dao.DAO;
import net.javs.dao.books.Book;
import net.javs.exceptions.InternalErrorException;
import net.javs.exceptions.NotFoundException;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * Chapter DAO
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

public class ChapterDAO extends DAO {
	
	public ChapterDAO(){

	}
	
	/**
	 * Gets a specific chapter
	 * Returns the DBObject that can later be turned into a map or string
	 * 
	 * @note Keeping it as a DBObject was the only way to keep the join _ids hashed
	 * @param chapterId
	 * @return DBObject that can later be turned into a map or string
	 */
	//Had to have this be a DBOject or would return _id in sub documents unhashed
	public DBObject getChapter(String chapterId){
		HashMap<String,Object> m = new HashMap<String,Object>();
		DBObject obj = null;	
		try{
			this.initMongo();			
			//first, see if the id is a MongoId
			BasicDBObject query = new BasicDBObject();
			if(ObjectId.isValid(chapterId)){
				query.append("_id", new ObjectId(chapterId));
			}else{
				query.append("oldId", chapterId);
			}
			DBCollection coll = db.getCollection("chapters");
			DBObject found = coll.findOne(query);	
			obj = found;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		if(obj==null){
			throw(new NotFoundException("That chapter could not be found"));
		}
		return obj;
	}

	/**
	 * Gets everything joined to this chapter
	 * 
	 * @param chapterId
	 * @param start
	 * @param count
	 * @return
	 */
	public ArrayList<Map> getContentForAChapter(String chapterId, int start, int count,Boolean isAdmin){
		Map t = getChapter(chapterId).toMap();
		
		//Because we can not filter on the sub document 
		//we want to grab all the content and filter it out
		//And as long as it actually has things joined
		if(t.containsKey("joins")){
			try{
				ArrayList<HashMap<String,Object>> c = (ArrayList<HashMap<String,Object>>) t.get("joins");
				
				//We build out list of ids for our two queries.
				//One with chapterIds one with contentIds
				ArrayList<ObjectId> chapterIds = new ArrayList<ObjectId>();
				ArrayList<ObjectId> contentIds = new ArrayList<ObjectId>();
				LinkedHashMap<String, Map> orderedIds = new LinkedHashMap<String, Map>();
				
				//As we loop through we also want to make sure that we let them change the start and limit of what is returned
				for(HashMap<String, Object> C : c.subList(start, Math.min(start + count, c.size()))){
					if(C.containsKey("_id") && C.containsKey("scope")){
						String _id = C.get("_id").toString();
						if(C.get("scope").equals("chapters")){
							chapterIds.add(new ObjectId(_id));
						}else{
							contentIds.add(new ObjectId(_id));
						}
						
						Map m = new HashMap<String,Object>();
						
						m.putAll(C);
						
						//We use this linked has map that has all the content in the right order
						// and also contains the join infermation
						//To maintain the order of all the ids
						orderedIds.put(_id, m);
					}
				}
				
				BasicDBObject inQuery = new BasicDBObject("$in",  contentIds);
				BasicDBObject query = new BasicDBObject("contentId" , inQuery);
				query.append("status", "Published");
				DBCollection coll = db.getCollection("content_versions");
				
				//Remove question pools for non admin
				//because only administrators can view those
				if(!isAdmin){
					query.append("type", new BasicDBObject("$ne", "questionpool"));
				}
				
				//For we only need these couple of fields that the content contains
				BasicDBObject fields = new BasicDBObject("title", 1);
				fields.append("type", 1);
				fields.append("contentId", 1);
				fields.append("publishedDate", 1);
				fields.append("body", 1);
				fields.append("users", 1);
				fields.append("costs",1);				
				fields.append("_id", 0);
				DBCursor cur = coll.find(query,fields);
				
				//Lets loop through the content results
				while(cur.hasNext()){
					DBObject obj = cur.next();
					Map tempMap = obj.toMap();
					Object tempId = tempMap.remove("contentId");
					tempMap.put("_id", tempId.toString());
					
					//Get the info that we already have for the content
					Map alreadyInfo = orderedIds.get(tempMap.get("_id").toString());
					tempMap.putAll(alreadyInfo);
					
					//Now throw this into the map that has the right order
					orderedIds.put(tempMap.get("_id").toString(), tempMap);
				}
				
				//Now do the same thing for chapters
				inQuery = new BasicDBObject("$in",  chapterIds);
				query = new BasicDBObject("_id" , inQuery);
				
				if(!isAdmin){
					query.append("adminOnly", new BasicDBObject("$ne","1"));
				}
				
				coll = db.getCollection("chapters");
				
				fields = new BasicDBObject("name", 1);
				fields.append("shortName", 1);
				fields.append("lcpCopyOfChapter", 1);
				fields.append("lcpLiveUpdates", 1);
				fields.append("imageId", 1);
				fields.append("joins.scope", 1);
				DBCursor chaptersCur = coll.find(query,fields);
				
				while(chaptersCur.hasNext()){
					DBObject obj = chaptersCur.next();
					Map tempMap = obj.toMap();
					Object title = tempMap.remove("name");
					tempMap.put("title", title);
					tempMap.put("type", "chapters");
					tempMap.put("_id", tempMap.get("_id").toString());
					
					//Get the info that we already have for the content
					Map alreadyInfo = orderedIds.get(tempMap.get("_id").toString());
					tempMap.putAll(alreadyInfo);
					
					orderedIds.put(tempMap.get("_id").toString(), tempMap);
				}
				
				ArrayList<Map> joins  = new ArrayList<Map>();
				
				//And now lets turn this linked hashmap back into an array with the right order
				for(String o : orderedIds.keySet()){  
					joins.add(orderedIds.get(o));
				}
				
				return joins;
			}catch(Exception e){
				throw(new InternalErrorException(e.toString()));
			}
		}else{
			return new ArrayList<Map>();
		}
	}
	
	/**
	 * Changes the order of the joins for a chapter
	 * 
	 * Will put joins in the order they are in the the chaptersList
	 * 
	 * @param chapterId
	 * @param chaptersList ArrayList of Maps that contain : "scope" and "scopeId" for each chapter, in the intended order
	 * @return boolean
	 */
	public boolean changeJoinOrder(String chapterId, ArrayList<Map> chaptersList){
		//As long as we are actually updating
		if(chaptersList.size() > 0){
			Map t = getChapter(chapterId).toMap();
			
			//Because we can not filter on the sub document 
			//we want to grab all the content and filter it out
			if(t.containsKey("joins")){
				try{
					String scope = "";
					String scopeId = "";
					ArrayList<String> ids = new ArrayList<String>();
					
					//Loop through the array that contains the new order
					for(Map m : chaptersList){
						if(m.containsKey("scope") && m.containsKey("scopeId")){
							scopeId = m.get("scopeId").toString();
							scope = m.get("scope").toString();
							
							//If this is actually a mongoId lets make it one
							if(ObjectId.isValid(scopeId)){
								ids.add(scopeId);
							}else{
								//Get the new id from the old id
								BasicDBObject scopeIdQuery = new BasicDBObject("oldId", scopeId);
								String collName = "";
								if(scope.equals("chapters")){
									collName = "chapters";
								}else if(scope.equals("content")){
									collName = "content";
								}else{
									return false;
								}
								
								DBCollection scopeColl = db.getCollection(collName);
								DBObject found = scopeColl.findOne(scopeIdQuery);			
								if(found != null){
									ids.add(found.get("_id").toString());
								}else{
									return false;
								}
							}
						}
					}
					ArrayList<HashMap<String,Object>> c = (ArrayList<HashMap<String,Object>>) t.get("joins");
					LinkedHashMap<String, Map> orderedIds = new LinkedHashMap<String, Map>();
					
					for(String id: ids){
						//To maintain the order of all the ids
						orderedIds.put(id, new HashMap<String, Object>());
					}
					
					for(HashMap<String, Object> C : c){
						if(C.containsKey("_id")){
							orderedIds.put(C.get("_id").toString(), C);
						}
					}
					
					//Throw in the join information in the new order for the mongo query
					BasicDBList joins  = new BasicDBList();
					for(String o : orderedIds.keySet()){
						joins.add(orderedIds.get(o));
					}
					
					BasicDBObject query;
					if(ObjectId.isValid(chapterId)){
						query = new BasicDBObject("_id", new ObjectId(chapterId));				
					}else{
						//they may be looking for an oldId
						query = new BasicDBObject("oldId", chapterId);
					}
					
					//And now replace the old joins with the new reorganized joins
					DBCollection coll = db.getCollection("chapters");
					BasicDBObject joinQuery = new BasicDBObject("joins", joins);
					BasicDBObject sub = new BasicDBObject("$set", joinQuery);
					coll.update(query, sub);
					
					return true;
				}catch(Exception e){
					e.printStackTrace();
					return false;
				}finally{
					this.deInitMongo();
				}
			}
		}else{
			throw(new InternalErrorException("A List of Ids is required."));
		}
		
		return false;
	}
	
	/**
	 * Add a piece of content to the content array of a Chapter object and takes LCP parameters
	 * 
	 * @param chapterId
	 * @param scopeId
	 * @param scope Must be either "content" or "chapters"
	 * @param joinType Must be either "primary" or "selected"
	 * @param featured If this is featured set to true
	 * @param hide Set to true to hide
	 * @param isLCP true if this is an LCPed scope
	 * @param lcpedFrom The book id this scope was LCPed from. If this is a copied chapter do not set to anything.
	 * @return
	 */
	public boolean addContentToChapter(String chapterId, String scopeId, String scope, String joinType,String featured, String hide, String isLCP,String lcpedFrom){
		boolean ret = false;
		BasicDBObject query;
		Book s = new Book();
		
		try{
			this.initMongo();
			//first, we need to see if this chapter/content pair already exists
			if(ObjectId.isValid(chapterId)){
				query = new BasicDBObject("_id", new ObjectId(chapterId));				
			}else{
				//they may be looking for an oldId
				query = new BasicDBObject("oldId", chapterId);
			}
			
			Boolean isLCPCopyOfChapter = false;
			Boolean isAdminOnly = false;
			
			if(!ObjectId.isValid(scopeId)){
				//Get the new id from the old id for the scope being joined
				BasicDBObject scopeIdQuery = new BasicDBObject("oldId", scopeId);
				String collName = "";
				if(scope.equals("chapters")){
					collName = "chapters";
				}else if(scope.equals("content")){
					collName = "content";
				}else{
					throw(new NotFoundException());
				}
				
				DBCollection scopeColl = db.getCollection(collName);
				
				DBObject found = scopeColl.findOne(scopeIdQuery);			
				if(found != null){
					scopeId = found.get("_id").toString();
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
			}
			
			//And lets see if this is already joined to a chapter
			//So we know whether to update a join or create a new one
			query.append("joins._id", new ObjectId(scopeId));
			DBCollection coll = db.getCollection("chapters");
			DBObject found = coll.findOne(query);
			
			if(found != null){
				//Update the existing join
				//We only want to update the fields that were sent to us
				BasicDBObject removeFields = new BasicDBObject();
				BasicDBObject contentDetails = new BasicDBObject();
				if(!scope.equals("")){
					contentDetails.append("joins.$.scope", scope);
				}
				if(!joinType.equals("")){
					contentDetails.append("joins.$.joinType", joinType);
				}
				
				if(featured.equals("1")){
					contentDetails.append("joins.$.featured", "1");
				}else if(featured.equals("0")){
					removeFields.append("joins.$.featured", 1);
				}
				
				
				if(hide.equals("1")){
					contentDetails.append("joins.$.hide", "1");
				}else if(hide.equals("0")){
					removeFields.append("joins.$.hide", 1);
				}
				
				if(isLCP.equals("1")){
					contentDetails.append("joins.$.isLCP", "1");
				}else if(isLCP.equals("0")){
					removeFields.append("joins.$.isLCP", 1);
				}
				
				if(lcpedFrom.length() > 0){
					contentDetails.append("joins.$.lcpedFrom", lcpedFrom);
				}else{
					removeFields.append("joins.$.lcpedFrom", 1);
				}
				
				//And now update and remove fields as nessasary
				BasicDBObject sub = new BasicDBObject("$set", contentDetails);
				sub.append("$unset", removeFields);
				coll.update(query, sub);
				ret = true;
			}else{
				//Add a join with the fields sent to us
				//we need to add it in
				query.removeField("joins._id");
				BasicDBObject contentDetails = new BasicDBObject("_id", new ObjectId(scopeId));
				contentDetails.append("scope", scope);
				contentDetails.append("joinType", joinType);
				
				if(featured.equals("1")){
					contentDetails.append("featured", "1");
				}
				
				if(hide.equals("1")){
					contentDetails.append("hide", "1");
				}
				
				if(isLCP.equals("1")){
					contentDetails.append("isLCP", "1");
				}
				
				if(lcpedFrom.length() > 0){
					contentDetails.append("lcpedFrom", lcpedFrom);
				}
				
				//And then lets add it ot the end of the joins array subdocument
				BasicDBObject content = new BasicDBObject("joins", contentDetails);
				BasicDBObject sub = new BasicDBObject("$push", content);
				coll.update(query, sub);
				ret = true;
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		return ret;
	}
	
	/**
	 * Removes content from a chapter
	 * 
	 * @param chapterId
	 * @param scopeId
	 * @param scope Must be either "content" or "chapters"
	 * @return
	 */
	public boolean removeContentFromChapter(String chapterId, String scopeId,String scope){
		boolean ret = false;
		BasicDBObject query;
		try{
			this.initMongo();
			//first, we need to see if this chapter/content pair already exists
			if(ObjectId.isValid(chapterId)){
				query = new BasicDBObject("_id", new ObjectId(chapterId));				
			}else{
				//they may be looking for an oldId
				query = new BasicDBObject("oldId", chapterId);
			}
			
			//Find the new id for this scope if this is an oldId
			if(!ObjectId.isValid(scopeId)){
				BasicDBObject scopeIdQuery = new BasicDBObject("oldId", scopeId);
				String collName = "";
				if(scope.equals("chapters")){
					collName = "chapters";
				}else if(scope.equals("content")){
					collName = "content";
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
				
				DBCollection scopeColl = db.getCollection(collName);
				DBObject found = scopeColl.findOne(scopeIdQuery);			
				if(found != null){
					scopeId = found.get("_id").toString();
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
			}
			
			query.append("joins._id", new ObjectId(scopeId));
			DBCollection coll = db.getCollection("chapters");
			DBObject foundChapter = coll.findOne(query);		
			
			//If this chapter doesn't exist then we don't need to remove a join
			if(foundChapter == null){
				ret = true;
			}else{
				//Now we want to pull the join from the joins array subdocument
				BasicDBObject findQuery = new BasicDBObject("_id",  foundChapter.get("_id"));
				
				BasicDBObject content = new BasicDBObject("_id",  new ObjectId(scopeId));
				BasicDBObject join = new BasicDBObject("joins",  content);
				BasicDBObject pullQuery = new BasicDBObject("$pull",  join);
				coll.update(findQuery, pullQuery);
				ret = true;
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		return ret;
	}
	
	/**
	 * Deletes this scope from all the chapters it is joined to
	 * 
	 * @param scopeId
	 * @param scope
	 * @return
	 */
	public boolean removeAllChapters(String scopeId, String scope){
		boolean ret = false;
		try{
			this.initMongo();
			//Find the new id for this scope if this is an oldId
			if(!ObjectId.isValid(scopeId)){
				BasicDBObject scopeIdQuery = new BasicDBObject("oldId", scopeId);
				String collName = "";
				if(scope.equals("chapters")){
					collName = "chapters";
				}else if(scope.equals("content")){
					collName = "content";
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
				
				DBCollection scopeColl = db.getCollection(collName);
				DBObject found = scopeColl.findOne(scopeIdQuery);			
				if(found != null){
					scopeId = found.get("_id").toString();
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
			}
			
			//Now lets build out our query
			ArrayList<BasicDBObject> query = new ArrayList<BasicDBObject>();
			query.add(new BasicDBObject("joins._id", new ObjectId(scopeId)));
			query.add(new BasicDBObject("joins.scope", scope));
			BasicDBObject findQuery = new BasicDBObject("$and", query);
			
			ArrayList<BasicDBObject> content = new ArrayList<BasicDBObject>();
			content.add(new BasicDBObject("_id",  new ObjectId(scopeId)));
			content.add(new BasicDBObject("scope", scope));
			
			BasicDBObject join = new BasicDBObject("joins",  new BasicDBObject("$and",content));
			BasicDBObject pullQuery = new BasicDBObject("$pull",  join);
			
			DBCollection coll = db.getCollection("chapters");
			
			//Pull this join sub document from every chapter that contains it
			coll.update(findQuery, pullQuery,false,true);
			ret = true;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		return ret;
	}
	
	/**
	 * Gets all the chapters this scope is joined to directly
	 * 
	 * @param scopeId
	 * @param scope Must be either "chapters" or "content".
	 * @return
	 */
	public ArrayList<DBObject> getAllChaptersForContent(String scopeId, String scope){
		ArrayList<DBObject> ret = new ArrayList<DBObject>();
		try{
			this.initMongo();
			//Find the new id for this scope if this is an oldId
			if(!ObjectId.isValid(scopeId)){
				BasicDBObject scopeIdQuery = new BasicDBObject("oldId", scopeId);
				String collName = "";
				if(scope.equals("chapters")){
					collName = "chapters";
				}else if(scope.equals("content")){
					collName = "content";
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
				
				DBCollection scopeColl = db.getCollection(collName);
				DBObject found = scopeColl.findOne(scopeIdQuery);	
				//If we found it we want to use this id
				if(found != null){
					scopeId = found.get("_id").toString();
				}else{
					throw(new NotFoundException("This " + scope + " was not found."));
				}
			}
			ArrayList<BasicDBObject> query = new ArrayList<BasicDBObject>();
			query.add(new BasicDBObject("joins._id", new ObjectId(scopeId)));
			
			BasicDBObject findQuery = new BasicDBObject("$and", query);
			
			BasicDBObject fields = getAllViewableFields();
			fields.removeField("joins");

			//Only grabs the relevant join
			fields.append("joins.$", 1);
			
			DBCollection coll = db.getCollection("chapters");

			//Pull this join sub document from every chapter that contains it
			DBCursor cur = coll.find(findQuery,fields);
			while(cur.hasNext()){
				DBObject obj = cur.next();
				ret.add(obj);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		return ret;
	}
	
	/**
	 * Gets the parent chapter for a chapter
	 * 
	 * @param chapterId
	 * @return
	 */
	public DBObject getParentChapterForChapter(String chapterId){
		DBObject ret = new BasicDBObject();
		try{
			this.initMongo();
			//Find the new id for this scope if this is an oldId
			if(!ObjectId.isValid(chapterId)){
				BasicDBObject scopeIdQuery = new BasicDBObject("oldId", chapterId);
				
				DBCollection scopeColl = db.getCollection("chapters");
				DBObject found = scopeColl.findOne(scopeIdQuery);			
				if(found != null){
					chapterId = found.get("_id").toString();
				}else{
					throw(new NotFoundException("This chapter was not found."));
				}
			}
			BasicDBObject query = new BasicDBObject();
			//If this Id exists in the subdocument of a chapter
			query.append("_id", new ObjectId(chapterId));
			//And it doesn't have the lcpedFrom field
			BasicDBObject exists = new BasicDBObject("$exists" , false);
			query.append("lcpedFrom", exists);
			
			BasicDBObject elmMatch = new BasicDBObject("$elemMatch", query);
			
			BasicDBObject findQuery = new BasicDBObject("joins", elmMatch);
			
			DBCollection coll = db.getCollection("chapters");

			//Pull this join sub document from every chapter that contains it
			
			DBObject obj = coll.findOne(findQuery);
			if(obj != null){
				ret =  obj;
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		return ret;
	}
	
	public Boolean deleteChapter(String chapterId){
		Boolean ret = false;
		try{
			this.initMongo();
			//Find the new id for this scope if this is an oldId
			BasicDBObject q = new BasicDBObject();
			if(ObjectId.isValid(chapterId)){
				q.append("_id", new ObjectId(chapterId));
			}else{
				BasicDBObject idQuery = new BasicDBObject("oldId", chapterId);
				DBCollection idColl = db.getCollection("chapters");
				DBObject found = idColl.findOne(idQuery);	
				if(found != null){
					q.append("_id", found.get("_id"));
				}else{
					return false;
				}
			}
			
			//Remove this chapter from all the places it is joined first
			ArrayList<BasicDBObject> query = new ArrayList<BasicDBObject>();
			query.add(new BasicDBObject("joins._id", new ObjectId(chapterId)));
			query.add(new BasicDBObject("joins.scope", "chapters"));
			BasicDBObject findQuery = new BasicDBObject("$and", query);
			
			ArrayList<BasicDBObject> content = new ArrayList<BasicDBObject>();
			content.add(new BasicDBObject("_id",  new ObjectId(chapterId)));
			content.add(new BasicDBObject("scope", "chapters"));
			
			BasicDBObject join = new BasicDBObject("joins",  new BasicDBObject("$and",content));
			BasicDBObject pullQuery = new BasicDBObject("$pull",  join);
			
			DBCollection coll = db.getCollection("chapters");
			
			//Pull this join sub document from every chapter that contains it
			coll.update(findQuery, pullQuery,false,true);
			
			//Then delete the chapter
			coll.remove(q);
			
			ret = true;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			this.deInitMongo();
		}
		
		return ret;
	}
	
	//returns a DB Object that can be passed as a field param for the find call
	//@Note: Used when we use sub document project because if we specify a subdocument field
	//       it will only return that sub document unless we specify all of the other fields aswell
	private BasicDBObject getAllViewableFields(){
		BasicDBObject fields = new BasicDBObject();
		fields.append("_id", 1);
		fields.append("name", 1);
		fields.append("description", 1);
		fields.append("abstract", 1);
		fields.append("shortName", 1);
		fields.append("imageId", 1);
		fields.append("slideShowId", 1);
		fields.append("mainFeature", 1);
		fields.append("inactive", 1);
		fields.append("displayImage", 1);
		fields.append("randomFeatures", 1);
		fields.append("showUpdatedContent", 1);
		fields.append("lcpCopyOfChapter", 1);
		fields.append("lcpLiveUpdates", 1);
		fields.append("lcpPrivateCommenting", 1);
		fields.append("joins", 1);
		fields.append("dateCreated", 1);
		fields.append("lastUpdatedTime", 1);
		fields.append("galleryId", 1);
		
		return fields;
	}
	
}
