from collections.abc import Iterable
from typing import Callable, TypeVar

T = TypeVar('T')
R = TypeVar('R')


def iterable_or_varargs(
        args: tuple,
        dispatch: Callable[[Iterable[T]], R] = lambda x: x,
        atomic: tuple = (str, bytes),
) -> R:
    """
    Simulates the overloads f(*items) and f(iterable_of_items):
    a single iterable argument is unpacked, unless it is an instance of one of the `atomic` types.
    """
    if len(args) == 1:
        item = args[0]
        if isinstance(item, Iterable) and not isinstance(item, atomic):
            return dispatch(item)
        return dispatch([item])
    return dispatch(args)
