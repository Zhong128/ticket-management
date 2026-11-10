package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.mapper.provider.UserSqlProvider;
import org.example.ticketmanagement.dto.UserProfileDTO;
import java.time.LocalDateTime;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.pojo.UserQuery;

import java.util.List;

/**
 * The interface User mapper.
 */
@Mapper
public interface UserMapper {
    /** 仅更新启用账号的头像及修改时间，不覆盖其他资料或账户状态。 */
    @Update("""
            UPDATE user
            SET avatar = #{avatar}, update_time = #{updateTime}
            WHERE id = #{id} AND status = 1
            """)
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar,
                     @Param("updateTime") LocalDateTime updateTime);

    /** 显式本地初始化时只新增管理员，用户名冲突由唯一约束拒绝。 */
    @Insert("""
            INSERT INTO user(username, password, email, nickname, role, status, create_time, update_time)
            VALUES(#{username}, #{password}, #{email}, #{nickname}, 'ADMIN', 1, #{createTime}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLocalAdmin(User user);

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
     * 新增用户
     */
    @Insert("insert into user(username,password,email,phone,nickname,avatar,real_name,id_card,gender,birthday,status,last_login_time,last_login_ip,create_time,update_time) " +
            "values(#{username},#{password},#{email},#{phone},#{nickname},#{avatar},#{realName},#{idCard},#{gender},#{birthday},#{status},#{lastLoginTime},#{lastLoginIp},#{createTime},#{updateTime})")
    void addUser(User user);

    /**
     * 按id删除普通用户，SQL条件阻止删除管理员。
     */
    @Delete("delete from user where id=#{id} AND role NOT IN ('ADMIN', 'SUPER_ADMIN')")
    void deleteUserById(Long id);

    /**
     * 修改用户
     */
    @Update("update user set username=#{username},password=#{password},email=#{email},phone=#{phone},nickname=#{nickname},avatar=#{avatar},real_name=#{realName},id_card=#{idCard},gender=#{gender},birthday=#{birthday},status=#{status},last_login_ip=#{lastLoginIp} where id=#{id}")
    void updateUser(User user);

    /**
     * 仅更新允许编辑的资料列，不覆盖并发改密或账户状态，空值保留原列。
     */
    @Update("""
            UPDATE user
            SET username = COALESCE(#{profile.username}, username),
                email = COALESCE(#{profile.email}, email),
                phone = COALESCE(#{profile.phone}, phone),
                nickname = COALESCE(#{profile.nickname}, nickname),
                real_name = COALESCE(#{profile.realName}, real_name),
                gender = COALESCE(#{profile.gender}, gender),
                birthday = COALESCE(#{profile.birthday}, birthday),
                update_time = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") Long id, @Param("profile") UserProfileDTO profile,
                      @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 仅更新允许编辑的资料列，不覆盖并发改密或账户状态，空值保留原列。
     */
    @Update("""
            UPDATE user
            SET username = COALESCE(#{profile.username}, username),
                email = COALESCE(#{profile.email}, email),
                phone = COALESCE(#{profile.phone}, phone),
                nickname = COALESCE(#{profile.nickname}, nickname),
                avatar = COALESCE(#{profile.avatar}, avatar),
                real_name = COALESCE(#{profile.realName}, real_name),
                gender = COALESCE(#{profile.gender}, gender),
                birthday = COALESCE(#{profile.birthday}, birthday),
                update_time = #{updatedAt}
            WHERE id = #{id} AND role NOT IN ('ADMIN', 'SUPER_ADMIN')
            """)
    int updateAdminProfile(@Param("id") Long id, @Param("profile") UserProfileDTO profile,
                      @Param("updatedAt") LocalDateTime updatedAt);

    /** 只更新密码与修改时间，避免旧用户快照覆盖并发禁用和资料变更。 */
    @Update("""
            UPDATE user
            SET password=#{password}, update_time=#{now}
            WHERE id=#{id}
            """)
    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("now") LocalDateTime now);

    /** 仅更新普通用户状态，不回写密码和资料快照。 */
    @Update("UPDATE user SET status=#{status}, update_time=#{now} WHERE id=#{id} AND role NOT IN ('ADMIN','SUPER_ADMIN')")
    int updateOrdinaryUserStatus(@Param("id") Long id, @Param("status") Integer status,
                                 @Param("now") LocalDateTime now);

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

    // UserMapper.java 中添加
    @Delete("DELETE FROM user WHERE username LIKE 'test%'")
    int deleteTestUsers();


}

