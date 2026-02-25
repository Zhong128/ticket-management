package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.mapper.provider.UserSqlProvider;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.pojo.UserQuery;

import java.util.List;

/**
 * The interface User mapper.
 */
@Mapper
public interface UserMapper {
    /**
     * 根据ID查询用户
     *
     * @param id the id
     * @return the user by id
     */
    @Select("select * from user where id=#{id}")
    User getUserById(Long id);

    /**
     * 根据用户名查询用户
     *
     * @param username the username
     * @return the user by username
     */
    @Select("select * from user where username=#{username}")
    User getUserByUsername(String username);

    /**
     * 根据用户名和密码查询用户
     */
    @Select("select * from user where username=#{username} and password=#{password}")
    User selectByUsernameAndPassword(User user);

    /**
     * 新增用户
     */
    @Insert("insert into user(username,password,email,phone,nickname,avatar,real_name,id_card,gender,birthday,status,last_login_time,last_login_ip,create_time,update_time) " +
            "values(#{username},#{password},#{email},#{phone},#{nickname},#{avatar},#{realName},#{idCard},#{gender},#{birthday},#{status},#{lastLoginTime},#{lastLoginIp},#{createTime},#{updateTime})")
    void addUser(User user);

    /**
     * 根据id删除用户
     */
    @Delete("delete from user where id=#{id}")
    void deleteUserById(Long id);

    /**
     * 修改用户
     */
    @Update("update user set username=#{username},password=#{password},email=#{email},phone=#{phone},nickname=#{nickname},avatar=#{avatar},real_name=#{realName},id_card=#{idCard},gender=#{gender},birthday=#{birthday},status=#{status},last_login_ip=#{lastLoginIp} where id=#{id}")
    void updateUser(User user);

    /**
     * 用户注册
     */
    @Insert("insert into user(username,password,email,phone,nickname,avatar,real_name,id_card,gender,birthday,status,last_login_time,last_login_ip,create_time,update_time) " +
            "values(#{username},#{password},#{email},#{phone},#{nickname},#{avatar},#{realName},#{idCard},#{gender},#{birthday},#{status},#{lastLoginTime},#{lastLoginIp},#{createTime},#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void registerUser(User user);

    /**
     * 根据邮箱查询用户
     */
    @Select("select * from user where email= #{email}")
    User getUserByEmail(String email);


    /**
     * 根据手机号查询用户
     */
    @Select("select * from user where phone= #{phone}")
    User getUserByPhone(String phone);

    /**
     * 根据openId查询用户
     */
    @Select("select * from user where open_id = #{openId} and status = 1")
    User getUserByOpenId(@Param("openId") String openId);

    /**
     * 更新用户微信信息
     */
    @Update("update user set open_id=#{openId}, union_id=#{unionId}, nickname=COALESCE(#{nickname}, nickname), avatar=COALESCE(#{avatar}, avatar), last_login_time=#{lastLoginTime} where id=#{id}")
    void updateUserWechatInfo(User user);

    /**
     * 插入微信用户
     */
    @Insert("insert into user(open_id, union_id, nickname, avatar, username, password, email, status, last_login_time, create_time, update_time) " +
            "values(#{openId}, #{unionId}, #{nickname}, #{avatar}, #{username}, '', #{email}, 1, #{lastLoginTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertWechatUser(User user);

    /**
     * 分页查询用户列表
     */
    @SelectProvider(type = UserSqlProvider.class, method = "selectUsersByPage")
    List<User> selectUsersByPage(UserQuery query);

    /**
     * 统计用户数量（用于分页）
     */
    @SelectProvider(type = UserSqlProvider.class, method = "countUsers")
    Long countUsers(UserQuery query);

    /**
     * 根据多个ID查询用户
     */
    @Select("<script>" +
            "SELECT * FROM user WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<User> selectUsersByIds(@Param("ids") List<Long> ids);


}

