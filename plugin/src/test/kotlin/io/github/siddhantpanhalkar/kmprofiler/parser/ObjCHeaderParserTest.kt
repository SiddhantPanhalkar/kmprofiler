package io.github.siddhantpanhalkar.kmprofiler.parser

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjCHeaderParserTest {

    private val parser = ObjCHeaderParser()

    @Test
    fun `parses simple interface as class`() {
        val header = """
            @interface Foo : KotlinBase
            - (instancetype)init;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        val decl = result.single()
        assertThat(decl.name).isEqualTo("Foo")
        assertThat(decl.kind).isEqualTo(DeclarationKind.CLASS)
        assertThat(decl.selectors).containsExactly("init")
        assertThat(decl.memberCount).isEqualTo(1)
    }

    @Test
    fun `parses protocol`() {
        val header = """
            @protocol BarProtocol
            - (void)bar;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        val decl = result.single()
        assertThat(decl.name).isEqualTo("BarProtocol")
        assertThat(decl.kind).isEqualTo(DeclarationKind.PROTOCOL)
        assertThat(decl.selectors).containsExactly("bar")
        assertThat(decl.memberCount).isEqualTo(1)
    }

    @Test
    fun `parses category extension as class with base name`() {
        val header = """
            @interface Foo (Extensions)
            - (void)extensionMethod;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        val decl = result.single()
        assertThat(decl.name).isEqualTo("Foo")
        assertThat(decl.kind).isEqualTo(DeclarationKind.CLASS)
        assertThat(decl.selectors).containsExactly("extensionMethod")
    }

    @Test
    fun `extracts multi parameter selector`() {
        val header = """
            @interface Baz
            - (void)doWithA:(int)a b:(int)b c:(int)c;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("doWithA:b:c:")
        assertThat(decl.memberCount).isEqualTo(1)
    }

    @Test
    fun `extracts selector with pointer return type`() {
        val header = """
            @interface Foo : KotlinBase
            - (NSString *)title;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("title")
        assertThat(decl.memberCount).isEqualTo(1)
    }

    @Test
    fun `counts class methods`() {
        val header = """
            @interface Foo : KotlinBase
            + (instancetype)shared;
            - (void)reset;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("shared", "reset")
        assertThat(decl.memberCount).isEqualTo(2)
    }

    @Test
    fun `counts property lines in memberCount but not as selectors`() {
        val header = """
            @interface Foo : KotlinBase
            @property (nonatomic, strong) NSString *name;
            - (void)doIt;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("doIt")
        assertThat(decl.memberCount).isEqualTo(2)
    }

    @Test
    fun `skips unrecognised lines and comments`() {
        val header = """
            // A comment
            #import <Foundation/Foundation.h>
            @class Foo;
            @interface Foo : KotlinBase
            - (void)doIt;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        assertThat(result.single().memberCount).isEqualTo(1)
    }

    @Test
    fun `tolerates unclosed interface at eof`() {
        val header = """
            @interface Foo : KotlinBase
            - (void)doIt;
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        val decl = result.single()
        assertThat(decl.name).isEqualTo("Foo")
        assertThat(decl.memberCount).isEqualTo(1)
        assertThat(decl.selectors).containsExactly("doIt")
    }

    @Test
    fun `parses multiple declarations in one header`() {
        val header = """
            @interface A : KotlinBase
            @end
            @interface B : KotlinBase
            @end
            @protocol P
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.name }).containsExactly("A", "B", "P")
        assertThat(result.map { it.kind }).containsExactly(
            DeclarationKind.CLASS,
            DeclarationKind.CLASS,
            DeclarationKind.PROTOCOL,
        )
    }

    @Test
    fun `returns empty list for empty input`() {
        assertThat(parser.parse("")).isEmpty()
        assertThat(parser.parse("\n\n")).isEmpty()
    }

    @Test
    fun `handles stray end without open block`() {
        val header = "@end"
        assertThat(parser.parse(header)).isEmpty()
    }

    @Test
    fun `method line without selector does not add selector`() {
        val header = """
            @interface Foo : KotlinBase
            - (void);
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.memberCount).isEqualTo(1)
        assertThat(decl.selectors).isEmpty()
    }

    @Test
    fun `method line outside a block does not crash and is ignored`() {
        val header = """
            + (instancetype)shared;
            @interface Foo : KotlinBase
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        assertThat(result.single().memberCount).isZero()
    }

    @Test
    fun `opens new block flushes previously unclosed block`() {
        val header = """
            @interface A : KotlinBase
            @interface B : KotlinBase
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactly("A", "B")
        assertThat(result.map { it.kind }).containsExactly(
            DeclarationKind.CLASS,
            DeclarationKind.CLASS,
        )
    }

    @Test
    fun `method line with blank selector text yields no selector`() {
        val header = """
            @interface Foo : KotlinBase
            - (void)
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.memberCount).isEqualTo(1)
        assertThat(decl.selectors).isEmpty()
    }

    @Test
    fun `uses swift_name attribute for class name instead of objc prefixed name`() {
        val header = """
            __attribute__((objc_runtime_name("SharedUserRepository")))
            __attribute__((swift_name("UserRepository")))
            @interface SharedUserRepository : KotlinBase
            - (instancetype)init;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.name).isEqualTo("UserRepository")
        assertThat(decl.kind).isEqualTo(DeclarationKind.CLASS)
    }

    @Test
    fun `uses swift_name attribute for method selector`() {
        val header = """
            @interface UserRepository : KotlinBase
            - (NSString *)getUserId:(NSString *)id __attribute__((swift_name("getUser(id:)")));
            - (void)clearCache;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("getUser(id:)", "clearCache")
        assertThat(decl.memberCount).isEqualTo(2)
    }

    @Test
    fun `skips kotlin runtime infrastructure declarations`() {
        val header = """
            __attribute__((swift_name("KotlinBase")))
            @interface KotlinBase
            @end
            __attribute__((swift_name("KotlinNumber")))
            @interface KotlinNumber : KotlinBase
            @end
            @interface SharedUserRepository : KotlinBase
            - (void)doIt;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("SharedUserRepository")
    }

    @Test
    fun `skips foundation root category extensions`() {
        val header = """
            @interface NSError (kotlinException)
            - (instancetype)kotlinException;
            @end
            @interface Foo : KotlinBase
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("Foo")
    }

    @Test
    fun `keeps category extensions on non-foundation types`() {
        val header = """
            @interface Foo (Extensions)
            - (void)extensionMethod;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.name).isEqualTo("Foo")
        assertThat(decl.selectors).containsExactly("extensionMethod")
    }

    @Test
    fun `skips category extension on skipped runtime root`() {
        val header = """
            __attribute__((swift_name("KotlinBase")))
            @interface SharedBase : NSObject
            @end
            @interface SharedBase (SharedBaseCopying) <NSCopying>
            @end
            @interface SharedUserRepository : SharedBase
            - (void)doIt;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("SharedUserRepository")
    }

    @Test
    fun `strips generic type parameters from interface name`() {
        val header = """
            @interface UserRepository<Element> : KotlinBase
            - (void)doIt;
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.name).isEqualTo("UserRepository")
        assertThat(decl.selectors).containsExactly("doIt")
    }

    @Test
    fun `truncates method line at attribute marker when no swift name present`() {
        val header = """
            @interface Foo : KotlinBase
            - (void)doIt __attribute__((objc_runtime_name("Something")));
            @end
        """.trimIndent()

        val decl = parser.parse(header).single()

        assertThat(decl.selectors).containsExactly("doIt")
    }

    @Test
    fun `skips bulk forward declaration line ending with semicolon`() {
        val header = """
            @protocol SharedAnalytics, SharedPlatform, SharedSubscriptionResult;
            @protocol SharedAnalytics
            - (void)track;
            @end
        """.trimIndent()

        val result = parser.parse(header)

        // Only the actual definition is parsed, not the forward declaration
        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("SharedAnalytics")
        assertThat(result.single().selectors).containsExactly("track")
    }

    @Test
    fun `skips single forward declaration ending with semicolon`() {
        val header = """
            @protocol Foo;
            @protocol Foo
            - (void)doIt;
            @end
        """.trimIndent()

        val result = parser.parse(header)
        assertThat(result).hasSize(1)
    }
}