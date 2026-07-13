package org.advgnd.atrium

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/api/v1")
class ApiV1 {
    @Serializable
    @Resource("auth/register")
    class Register(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("auth/login")
    class Login(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("auth/logout")
    class Logout(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("user/profile")
    class Profile(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("patients")
    class Patients(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("patients/{id}")
    class PatientDetail(val parent: ApiV1 = ApiV1(), val id: String)

    @Serializable
    @Resource("patients/{id}/visits")
    class PatientVisits(val parent: ApiV1 = ApiV1(), val id: String)

    @Serializable
    @Resource("visits")
    class Visits(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("visits/{id}")
    class VisitDetail(val parent: ApiV1 = ApiV1(), val id: String)

    @Serializable
    @Resource("inventory")
    class Inventory(val parent: ApiV1 = ApiV1())

    @Serializable
    @Resource("visits/{id}/pharmacy-orders")
    class VisitPharmacyOrders(val parent: ApiV1 = ApiV1(), val id: String)
}
