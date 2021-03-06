package com.writty.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.AR;
import com.blade.jdbc.Page;
import com.blade.jdbc.QueryParam;
import com.writty.kit.QiniuKit;
import com.writty.kit.Utils;
import com.writty.model.Post;
import com.writty.model.Special;
import com.writty.model.User;
import com.writty.service.FavoriteService;
import com.writty.service.PostService;
import com.writty.service.SpecialService;
import com.writty.service.UserService;

import blade.kit.DateKit;
import blade.kit.FileKit;
import blade.kit.StringKit;

@Service
public class PostServiceImpl implements PostService {
	
	@Inject
	private UserService userService;
	
	@Inject
	private SpecialService specialService;
	
	@Inject
	private FavoriteService favoriteService;
	
	@Override
	public Post getPost(String pid) {
		return AR.findById(Post.class, pid);
	}
	
	@Override
	public Page<Map<String, Object>> getPageListMap(Long uid, Long sid, Integer is_pub, String title,
			Integer page, Integer count, String orderby) {
		
		if(null == page || page < 1){
			page = 1;
		}
		if(null == count || count < 1){
			count = 10;
		}
		
		QueryParam up = QueryParam.me();
		if(null != uid){
			up.eq("uid", uid);
		}
		if(null != sid){
			up.eq("sid", sid);
		}
		if(null != is_pub){
			up.eq("is_pub", is_pub);
		}
		if(StringKit.isNotBlank(title)){
			up.like("title", "%"+title+"%");
		}
		up.eq("is_del", 0);
		if(StringKit.isBlank(orderby)){
			orderby = "created desc";
		}
		up.orderby(orderby).page(page, count);
		Page<Post> postPage = AR.find(up).page(Post.class);
		return this.getPageListMap(postPage);
	}
	
	private Page<Map<String, Object>> getPageListMap(Page<Post> postPage) {
		long totalCount = postPage.getTotalCount();
		int page = postPage.getPage();
		int pageSize = postPage.getPageSize();
		Page<Map<String, Object>> result = new Page<Map<String,Object>>(totalCount, page, pageSize);
		
		List<Post> posts = postPage.getResults();
		
		List<Map<String, Object>> postMaps = this.getListMap(posts);
		
		result.setResults(postMaps);
		return result;
	}

	private List<Map<String, Object>> getListMap(List<Post> posts) {
		List<Map<String, Object>> postMaps = new ArrayList<Map<String,Object>>();
		if(null != posts && posts.size() > 0){
			for(Post post : posts){
				Map<String, Object> map = this.getPostDetail(post, null);
				if(null != map && !map.isEmpty()){
					postMaps.add(map);
				}
			}
		}
		return postMaps;
	}

	@Override
	public Map<String, Object> getPostDetail(Post post, String pid) {
		if(null == post){
			post = this.getPost(pid);
		}
		if(null != post){
			Long uid = post.getUid();
			User user = userService.getUser(uid);
			if(null == user){
				return null;
			}
			
			Special special = specialService.getSpecial(post.getSid());
			if(null == special){
				return null;
			}
			
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("pid", post.getPid());
			map.put("sid", post.getSid());
			map.put("uid", post.getUid());
			map.put("title", post.getTitle());
			map.put("comments", post.getComments());
			map.put("special", special.getTitle());
			
			if(StringKit.isNotBlank(post.getCover())){
				map.put("cover", QiniuKit.getUrl(post.getCover()));
			} else {
				map.put("cover", QiniuKit.getUrl(special.getCover()));
			}
			map.put("content", Utils.markdown2html(post.getContent()));
			map.put("created", post.getCreated());
			map.put("create_date", DateKit.formatDateByUnixTime(post.getCreated().longValue(), "yyyy-MM-dd"));
			map.put("user_name", user.getUser_name());
			map.put("publish_user", user.getNick_name());
			map.put("user_avatar", user.getAvatar());
			map.put("type", post.getType());
			map.put("is_pub", post.getIs_pub());
			
			Long favorites = favoriteService.getFavoriteCount(post.getPid());
			map.put("favorites", favorites);
			
			return map;
		}
		return null;
	}

	@Override
	public boolean save( String title, String slug, Long uid, Long sid, Integer type, Integer is_pub, String cover, String content) {
		
		try {
			Integer time = DateKit.getCurrentUnixTime();
			
			String pid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
			
			String cover_key = "";
			File file = new File(cover);
			if(file.exists()){
				String ext = FileKit.getExtension(file.getName());
				if(StringKit.isBlank(ext)){
					ext = "png";
				}
				
				String key = "post/" + pid + "." + ext;
				
				boolean flag = QiniuKit.upload(file, key);
				if(flag){
					cover_key = key;
				}
			}
			
			AR.update("insert into t_post(pid, title, slug, uid, sid, cover, content, type, is_pub, created, updated) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					pid, title, slug, uid,  sid, cover_key, content, type, is_pub, time, time).executeUpdate();
			
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	@Override
	public boolean delete(String pid) {
		if(null != pid){
			AR.update("update t_post set is_del = 1 where pid = ?", pid).executeUpdate(true);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean audit(String pid) {
		if(null != pid){
			AR.update("update t_post set is_pub = 1 where pid = ?", pid).executeUpdate();
			return true;
		}
		return false;
	}

	@Override
	public List<Map<String, Object>> getListMap(Long uid, Long sid, Integer is_pub, String title, String orderby) {
		QueryParam up = QueryParam.me();
		if(null != uid){
			up.eq("uid", uid);
		}
		if(null != sid){
			up.eq("sid", sid);
		}
		if(null != is_pub){
			up.eq("is_pub", is_pub);
		}
		if(StringKit.isNotBlank(title)){
			up.like("title", "%"+title+"%");
		}
		up.eq("is_del", 0);
		if(StringKit.isBlank(orderby)){
			orderby = "created desc";
		}
		up.orderby(orderby);
		List<Post> posts = AR.find(up).list(Post.class);
		return this.getListMap(posts);
	}

	@Override
	public List<Post> getList(Long uid, Integer is_pub, String title, String orderby) {
		QueryParam up = QueryParam.me();
		if(null != uid){
			up.eq("uid", uid);
		}
		if(null != is_pub){
			up.eq("is_pub", is_pub);
		}
		if(StringKit.isNotBlank(title)){
			up.like("title", "%"+title+"%");
		}
		up.eq("is_del", 0);
		if(StringKit.isBlank(orderby)){
			orderby = "created desc";
		}
		up.orderby(orderby);
		List<Post> posts = AR.find(up).list(Post.class);
		return posts;
	}
	
}
