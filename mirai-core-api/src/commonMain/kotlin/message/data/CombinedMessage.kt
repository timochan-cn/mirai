/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.message.data

import net.mamoe.mirai.message.data.visitor.MessageVisitor
import net.mamoe.mirai.message.data.visitor.RecursiveMessageVisitor
import net.mamoe.mirai.message.data.visitor.accept
import net.mamoe.mirai.utils.MiraiInternalApi

/**
 * One after one, hierarchically.
 *
 * @since 2.11
 */
@MiraiInternalApi
public class CombinedMessage @MessageChainConstructor constructor(
    @MiraiInternalApi public val element: Message, // must not contain ConstrainSingle
    @MiraiInternalApi public val tail: Message, // same as above
    @MiraiInternalApi public override val hasConstrainSingle: Boolean
) : MessageChainImpl, List<SingleMessage> {
    override fun <D, R> accept(visitor: MessageVisitor<D, R>, data: D): R {
        return visitor.visitCombinedMessage(this, data)
    }

    override fun <D> acceptChildren(visitor: MessageVisitor<D, *>, data: D) {
        element.acceptChildren(visitor, data)
        tail.acceptChildren(visitor, data)
    }

    override val size: Int by lazy {
        var size = 0
        element.accept(object : RecursiveMessageVisitor<Unit>() {
            override fun visitMessageChain(messageChain: MessageChain, data: Unit) {
                if (messageChain is DirectSizeAccess) {
                    size += messageChain.size
                    return super.visitMessageChain(messageChain, data)
                }
                return super.visitMessageChain(messageChain, data)
            }

            override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                size++
            }
        })
        size
    }

    override fun isEmpty(): Boolean =
        element is MessageChain && element.isEmpty() && tail is MessageChain && tail.isEmpty()

    override fun contains(element: SingleMessage): Boolean {
        if (this.element == element) return true
        if (this.tail == element) return true
        if (this.element is MessageChain && this.element.contains(element)) return true
        if (this.tail is MessageChain && this.tail.contains(element)) return true
        return false
    }


    private val toStringTemp: String by lazy {
        buildString {
            accept(object : RecursiveMessageVisitor<Unit>() {
                override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                    append(message.toString())
                }

                override fun visitMessageChain(messageChain: MessageChain, data: Unit) {
                    if (messageChain is DirectToStringAccess) {
                        append(messageChain.toString())
                    } else {
                        super.visitMessageChain(messageChain, data)
                    }
                }
            })
        }
    }

    private val contentToStingTemp: String by lazy {
        buildString {
            accept(object : RecursiveMessageVisitor<Unit>() {
                override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                    append(message.contentToString())
                }

                override fun visitMessageChain(messageChain: MessageChain, data: Unit) {
                    if (messageChain is DirectToStringAccess) {
                        append(messageChain.contentToString())
                    } else {
                        super.visitMessageChain(messageChain, data)
                    }
                }
            })
        }
    }

    override fun toString(): String = toStringTemp
    override fun contentToString(): String = contentToStingTemp

    override fun containsAll(elements: Collection<SingleMessage>): Boolean {
        if (elements.isEmpty()) return true
        if (this.isEmpty()) return false
        val remaining = elements.toMutableList()
        accept(object : RecursiveMessageVisitor<Unit>() {
            override fun isFinished(): Boolean = remaining.isEmpty()
            override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                remaining.remove(message)
            }
        })
        return remaining.isEmpty()
    }

    override fun iterator(): Iterator<SingleMessage> {
        suspend fun SequenceScope<SingleMessage>.yieldMessage(element: Message) {
            if (element is SingleMessage) {
                yield(element)
            } else {
                yieldAll((element as MessageChain).iterator())
            }
        }

        return iterator {
            yieldMessage(element)
            yieldMessage(tail)
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<SingleMessage> {
        if (fromIndex < 0 || fromIndex > toIndex) throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex")

        return buildList {
            accept(object : RecursiveMessageVisitor<Unit>() {
                private var currentIndex = 0
                override fun isFinished(): Boolean = currentIndex >= toIndex

                override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                    if (!isFinished()) add(message)
                    currentIndex++
                }
            })
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // slow operations
    ///////////////////////////////////////////////////////////////////////////

    private val slowList: MessageChain by lazy {
        sequenceOf(element, tail).toMessageChain()
    }

    // [MessageChain] implements [RandomAccess] so we should ensure that property here.
    override fun get(index: Int): SingleMessage = slowList[index]
    override fun indexOf(element: SingleMessage): Int = slowList.indexOf(element)
    override fun lastIndexOf(element: SingleMessage): Int = slowList.lastIndexOf(element)
    override fun listIterator(): ListIterator<SingleMessage> = slowList.listIterator()
    override fun listIterator(index: Int): ListIterator<SingleMessage> = slowList.listIterator(index)

}

internal interface DirectSizeAccess : MessageChain
internal interface DirectToStringAccess : MessageChain