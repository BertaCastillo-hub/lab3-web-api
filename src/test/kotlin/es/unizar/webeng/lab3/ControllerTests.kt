package es.unizar.webeng.lab3

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.Optional

private val MANAGER_REQUEST_BODY = { name: String ->
    """
    { 
        "role": "Manager", 
        "name": "$name" 
    }
    """
}

private val MANAGER_RESPONSE_BODY = { name: String, id: Int ->
    """
    { 
       "name" : "$name",
       "role" : "Manager",
       "id" : $id
    }
    """
}

private val MANAGER_LIST_BODY =
    """
    [{
       "name" : "Mary",
       "role" : "Manager",
       "id" : 1
    }]
    """

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ControllerTests {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    private lateinit var employeeRepository: EmployeeRepository

    @Test
    fun `POST is not safe and not idempotent`() {
        // SETUP
        // Initial state: the collection is empty. No employee exists.
        // Runtime state: the first save stores Mary, Manager, as id 1. The second save stores Mary, Manager, as id 2.
        // Each save allocates a new id (1, then 2): the server state changes on every call.
        every {
            employeeRepository.save(any<Employee>())
        } answers {
            Employee("Mary", "Manager", 1)
        } andThenAnswer {
            Employee("Mary", "Manager", 2)
        }

        mvc
            .post("/employees") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Mary")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Mary", 1))
                }
            }

        mvc
            .post("/employees") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Mary")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Location", "http://localhost/employees/2") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Mary", 2))
                }
            }

        // VERIFY
        // Not safe: save is called. Not idempotent: two identical requests produce two saves
        // and two different ids. The controller discards the client id, so both saves get id = null.
        verify(exactly = 2) {
            employeeRepository.save(Employee("Mary", "Manager", null))
        }
        // POST only creates: it never reads, lists or deletes.
        verify(exactly = 0) {
            employeeRepository.findById(any())
            employeeRepository.findAll()
            employeeRepository.deleteById(any())
        }
    }

    @Test
    fun `GET is safe and idempotent`() {
        // SETUP
        // Initial state: /employees/1 is Mary, Manager, id 1. /employees/2 has no representation.
        // Runtime state: both stay that way.
        // Id 1 always exists and id 2 never does: the answers are fixed because a read changes nothing.
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.of(Employee("Mary", "Manager", 1))
        }
        every {
            employeeRepository.findById(2)
        } answers {
            Optional.empty()
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/2").andExpect {
            status { isNotFound() }
        }

        mvc.get("/employees/2").andExpect {
            status { isNotFound() }
        }

        // VERIFY
        // Count how many times each method was called based on the test requests.
        // Idempotent: each id is read twice and both responses match (200 twice, 404 twice).
        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
        verify(exactly = 2) {
            employeeRepository.findById(2)
        }
        // Safe: no write (save, deleteById). The item GET does not list the collection either.
        verify(exactly = 0) {
            employeeRepository.save(any())
            employeeRepository.deleteById(any())
            employeeRepository.findAll()
        }
    }

    @Test
    fun `PUT is idempotent but not safe`() {
        // SETUP
        // Initial state: /employees/1 has no representation.
        // Runtime state: the first PUT stores Tom, Manager, at id 1. The second PUT leaves that same employee.
        // First lookup: id 1 is empty, so the controller creates it (201).
        // Second lookup: Tom is already there, so the controller replaces it (200).
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.empty()
        } andThenAnswer {
            Optional.of(Employee("Tom", "Manager", 1))
        }
        // Both saves store the same employee at id 1: no new id is allocated.
        every {
            employeeRepository.save(any<Employee>())
        } answers {
            Employee("Tom", "Manager", 1)
        }

        mvc
            .put("/employees/1") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Tom")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Content-Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Tom", 1))
                }
            }

        mvc
            .put("/employees/1") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Tom")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                header { string("Content-Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Tom", 1))
                }
            }

        // VERIFY
        // Not safe: save is called. Idempotent: both saves store exactly the same state
        // (Tom, Manager, id 1), even though the status changes from 201 to 200.
        verify(exactly = 2) {
            employeeRepository.save(Employee("Tom", "Manager", 1))
        }
        // Each PUT looks up the target URI first to choose between create and replace.
        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
        // PUT replaces one resource: it never deletes or lists.
        verify(exactly = 0) {
            employeeRepository.deleteById(any())
            employeeRepository.findAll()
        }
    }

    @Test
    fun `DELETE is idempotent but not safe`() {
        // SETUP
        // Initial state: /employees/1 is associated with an employee.
        // Runtime state: the first DELETE removes that association. The second DELETE finds it already gone.
        // deleteById returns Unit, so justRun stubs it. It does not fail when the id is already gone.
        justRun {
            employeeRepository.deleteById(1)
        }

        mvc.delete("/employees/1").andExpect {
            status { isNoContent() }
        }

        mvc.delete("/employees/1").andExpect {
            status { isNoContent() }
        }

        // VERIFY
        // Not safe: deleteById is called. Idempotent: the second call leaves the same state
        // (id 1 absent) and still returns 204.
        verify(exactly = 2) {
            employeeRepository.deleteById(1)
        }
        // DELETE does not check existence first, create anything or list the collection.
        verify(exactly = 0) {
            employeeRepository.save(any())
            employeeRepository.findById(any())
            employeeRepository.findAll()
        }
    }

    @Test
    fun `GET collection is safe and idempotent`() {
        // SETUP
        // Initial state: the collection contains exactly Mary, Manager, id 1.
        // Runtime state: the second GET sees that same list.
        // The collection always contains only Mary: a list request changes nothing.
        every {
            employeeRepository.findAll()
        } answers {
            listOf(Employee("Mary", "Manager", 1))
        }

        mvc.get("/employees").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_LIST_BODY)
            }
        }

        mvc.get("/employees").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_LIST_BODY)
            }
        }

        // VERIFY
        // Idempotent: the list is read twice and both responses are identical.
        verify(exactly = 2) {
            employeeRepository.findAll()
        }
        // Safe: no write (save, deleteById). Listing does not read a single id.
        verify(exactly = 0) {
            employeeRepository.save(any())
            employeeRepository.deleteById(any())
            employeeRepository.findById(any())
        }
    }
}
