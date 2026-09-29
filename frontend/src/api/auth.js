import { client } from './client'

/** 注册。成功返回 201 + 用户信息(不含密码)。 */
export const register = (payload) => client.post('/auth/register', payload)

/** 登录。成功返回 { token, tokenType, expiresIn, user }。 */
export const login = (payload) => client.post('/auth/login', payload)

/** 当前登录用户。 */
export const fetchCurrentUser = () => client.get('/users/me')
